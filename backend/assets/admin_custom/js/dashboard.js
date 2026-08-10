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

    var grid = themeColor('--hairline-color', '#e0e0e0');
    var quiet = themeColor('--body-quiet-color', '#666');

    // Fixed series colours. These are data identities, not theme colours: the
    // revenue line must stay the same hue in both themes or the two charts stop
    // being comparable across a toggle.
    var TEAL = '#1a7a7a';
    var AMBER = '#d68910';
    var BLUE = '#2d6ca2';
    var GREEN = '#1a7a45';

    Chart.defaults.font.family = "Vazirmatn, Tahoma, sans-serif";
    Chart.defaults.color = quiet;

    function baseOptions(yTitle) {
        return {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                // rtl on both: without it the legend swatch sits to the right of
                // its Persian label and the tooltip body reads inside-out.
                legend: { rtl: true, textDirection: 'rtl', labels: { boxWidth: 12 } },
                tooltip: { rtl: true, textDirection: 'rtl' }
            },
            scales: {
                x: {
                    grid: { color: grid },
                    ticks: {
                        color: quiet,
                        // 30 labels do not fit; show roughly every fifth.
                        maxTicksLimit: 8,
                        autoSkip: true
                    }
                },
                y: {
                    beginAtZero: true,
                    grid: { color: grid },
                    title: { display: !!yTitle, text: yTitle, color: quiet },
                    ticks: {
                        color: quiet,
                        // Integer ticks only: half a user and half a Toman are
                        // both meaningless.
                        precision: 0,
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
        draw('nb-chart-revenue', {
            type: 'line',
            data: {
                labels: data.labels,
                datasets: [
                    {
                        label: 'مجموع',
                        data: data.revenue.total,
                        borderColor: TEAL,
                        backgroundColor: 'rgba(26,122,122,0.12)',
                        fill: true,
                        tension: 0.25,
                        borderWidth: 2,
                        pointRadius: 0,
                        pointHoverRadius: 4
                    },
                    {
                        label: 'اشتراک',
                        data: data.revenue.subscription,
                        borderColor: BLUE,
                        borderWidth: 1.5,
                        borderDash: [4, 3],
                        pointRadius: 0,
                        pointHoverRadius: 4,
                        fill: false,
                        tension: 0.25
                    },
                    {
                        label: 'بسته‌های افزودنی',
                        data: data.revenue.addon,
                        borderColor: AMBER,
                        borderWidth: 1.5,
                        borderDash: [4, 3],
                        pointRadius: 0,
                        pointHoverRadius: 4,
                        fill: false,
                        tension: 0.25
                    }
                ]
            },
            options: baseOptions('تومان')
        });
    }

    if (data.growth) {
        var growthSets = [];
        if (data.growth.users) {
            growthSets.push({
                label: 'کاربر جدید',
                data: data.growth.users,
                backgroundColor: BLUE
            });
        }
        if (data.growth.businesses) {
            growthSets.push({
                label: 'کسب‌وکار جدید',
                data: data.growth.businesses,
                backgroundColor: GREEN
            });
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
