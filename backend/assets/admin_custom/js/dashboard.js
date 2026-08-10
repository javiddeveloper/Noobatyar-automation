/*
 * admin_custom/js/dashboard.js — the two charts on the admin index.
 *
 * Chart.js is vendored next to this file (chart.umd.js) and loaded with a
 * <script> tag from index.html. It is never fetched from a CDN: the production
 * server is in Iran, where jsdelivr/unpkg/cdnjs are unreachable, and a failed
 * CDN load would leave two blank boxes with no explanation.
 *
 * The data arrives through `{{ ... |json_script }}`, not through an inline
 * literal: json_script escapes for the HTML context properly, and it keeps this
 * file static so it can be cached and collectstatic-hashed.
 *
 * A series the viewer has no permission to see is sent as null rather than an
 * empty array (see core/dashboard/panels.py), so "not allowed" and "allowed but
 * all zeroes" stay distinguishable — the first hides the chart, the second
 * draws a flat line.
 */
(function () {
    'use strict';

    var node = document.getElementById('nb-dashboard-data');
    if (!node || typeof Chart === 'undefined') {
        return;
    }

    var data;
    try {
        data = JSON.parse(node.textContent);
    } catch (err) {
        return;
    }

    // Read the admin's own theme variables instead of hard-coding hex values,
    // so the charts follow the light/dark toggle like the rest of the page.
    var css = getComputedStyle(document.documentElement);
    function themeColor(name, fallback) {
        var value = css.getPropertyValue(name).trim();
        return value || fallback;
    }

    // report.css owns the palette, so the charts read their neutrals from the
    // same tokens as the cards they sit in rather than keeping a second, quietly
    // diverging copy of the theme.
    var grid = themeColor('--nb-line-soft', themeColor('--hairline-color', '#e0e0e0'));
    var axis = themeColor('--nb-line', '#e0e0e0');
    var quiet = themeColor('--nb-muted', themeColor('--body-quiet-color', '#666'));
    var surface = themeColor('--nb-surface', '#fff');

    // Fixed series colours, from brand/README.md. These are data identities,
    // not theme colours: the revenue line must stay the same hue in both themes
    // or the two charts stop being comparable across a toggle.
    var VIOLET = '#7c3aed';
    var AMBER = '#f59e0b';
    var BLUE = '#2563eb';
    var GREEN = '#15803d';

    Chart.defaults.font.family = "Vazirmatn, Tahoma, sans-serif";
    Chart.defaults.font.size = 11;
    Chart.defaults.color = quiet;

    function baseOptions(yTitle) {
        return {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                // rtl on both: without it the legend swatch sits to the right of
                // its Persian label and the tooltip body reads inside-out.
                legend: {
                    rtl: true,
                    textDirection: 'rtl',
                    align: 'end',
                    labels: {
                        // Round swatches, not squares: they read as data points
                        // rather than as another set of boxes on a page that is
                        // already all rectangles.
                        usePointStyle: true,
                        pointStyle: 'circle',
                        boxWidth: 8,
                        boxHeight: 8,
                        padding: 14
                    }
                },
                tooltip: {
                    rtl: true,
                    textDirection: 'rtl',
                    // Chart.js defaults to a black box, which floats over a dark
                    // theme as a hole. Painting it on the card surface with a
                    // hairline keeps it part of the page in both themes.
                    backgroundColor: surface,
                    titleColor: quiet,
                    bodyColor: themeColor('--nb-fg', '#23212b'),
                    borderColor: axis,
                    borderWidth: 1,
                    padding: 10,
                    cornerRadius: 8,
                    displayColors: true,
                    usePointStyle: true,
                    callbacks: {
                        // Latin grouping on the figures inside an otherwise
                        // Persian tooltip — same convention as the cards.
                        label: function (ctx) {
                            var v = ctx.parsed.y;
                            return ' ' + ctx.dataset.label + ': ' +
                                (v == null ? '—' : Number(v).toLocaleString('en-US'));
                        }
                    }
                }
            },
            scales: {
                x: {
                    // No vertical rules: with 30 daily points they turn the plot
                    // into graph paper and compete with the series itself.
                    grid: { display: false },
                    border: { color: axis },
                    ticks: {
                        color: quiet,
                        // 30 labels do not fit; show roughly every fifth.
                        maxTicksLimit: 8,
                        autoSkip: true,
                        maxRotation: 0,
                        padding: 6
                    }
                },
                y: {
                    beginAtZero: true,
                    // Dotted, not solid: these rules exist to let the eye carry
                    // a value across to the axis, and a solid rule at that job
                    // competes with the series drawn on top of it. drawTicks
                    // off because the rule already reaches the label.
                    //
                    // The dash goes on `border`, not on `grid`. Chart.js 4
                    // moved the v3 `grid.borderDash` option onto `border.dash`,
                    // and the old spelling is silently ignored — set it on
                    // `grid` here and the lines just come out solid.
                    grid: { color: grid, drawTicks: false },
                    border: { display: false, dash: [3, 3] },
                    title: { display: !!yTitle, text: yTitle, color: quiet, padding: 4 },
                    ticks: {
                        color: quiet,
                        // Integer ticks only: half a user and half a Toman are
                        // both meaningless.
                        precision: 0,
                        padding: 8,
                        maxTicksLimit: 6,
                        callback: function (value) {
                            return Number(value).toLocaleString('en-US');
                        }
                    }
                }
            }
        };
    }

    function draw(canvasId, config) {
        var canvas = document.getElementById(canvasId);
        if (canvas) {
            new Chart(canvas.getContext('2d'), config);
        }
    }

    if (data.revenue) {
        // The total is the subject of the chart and gets the filled area; the
        // two components that add up to it are dashed hairlines, so a reader
        // sees one shape with its breakdown rather than three peer lines.
        var revenueCanvas = document.getElementById('nb-chart-revenue');
        var totalFill = 'rgba(124,58,237,0.14)';
        if (revenueCanvas) {
            // A vertical gradient instead of a flat wash: the area stays legible
            // where it is tall and fades out before it can swallow the x-axis
            // labels underneath.
            var ctx2d = revenueCanvas.getContext('2d');
            var gradient = ctx2d.createLinearGradient(0, 0, 0, 250);
            gradient.addColorStop(0, 'rgba(124,58,237,0.28)');
            gradient.addColorStop(1, 'rgba(124,58,237,0.02)');
            totalFill = gradient;
        }

        draw('nb-chart-revenue', {
            type: 'line',
            data: {
                labels: data.labels,
                datasets: [
                    {
                        label: 'مجموع',
                        data: data.revenue.total,
                        borderColor: VIOLET,
                        backgroundColor: totalFill,
                        fill: true,
                        tension: 0.35,
                        borderWidth: 2.5,
                        pointRadius: 0,
                        pointHoverRadius: 4,
                        pointHoverBackgroundColor: VIOLET,
                        pointHoverBorderColor: surface,
                        pointHoverBorderWidth: 2
                    },
                    {
                        label: 'اشتراک',
                        data: data.revenue.subscription,
                        borderColor: BLUE,
                        borderWidth: 1.5,
                        borderDash: [4, 3],
                        pointRadius: 0,
                        pointHoverRadius: 3,
                        fill: false,
                        tension: 0.35
                    },
                    {
                        label: 'بسته‌های افزودنی',
                        data: data.revenue.addon,
                        borderColor: AMBER,
                        borderWidth: 1.5,
                        borderDash: [4, 3],
                        pointRadius: 0,
                        pointHoverRadius: 3,
                        fill: false,
                        tension: 0.35
                    }
                ]
            },
            options: baseOptions('تومان')
        });
    }

    if (data.growth) {
        var growthSets = [];
        // maxBarThickness, not a fixed width: over a 7-day range the bars would
        // otherwise stretch into slabs, and over 90 days they collapse to
        // threads. Rounded caps to match the card radii around them.
        var bar = { borderRadius: 4, borderSkipped: false, maxBarThickness: 22 };
        if (data.growth.users) {
            growthSets.push(Object.assign({
                label: 'کاربر جدید',
                data: data.growth.users,
                backgroundColor: BLUE,
                hoverBackgroundColor: BLUE
            }, bar));
        }
        if (data.growth.businesses) {
            growthSets.push(Object.assign({
                label: 'کسب‌وکار جدید',
                data: data.growth.businesses,
                backgroundColor: GREEN,
                hoverBackgroundColor: GREEN
            }, bar));
        }
        if (growthSets.length) {
            draw('nb-chart-growth', {
                type: 'bar',
                data: { labels: data.labels, datasets: growthSets },
                options: baseOptions('')
            });
        }
    }
}());
