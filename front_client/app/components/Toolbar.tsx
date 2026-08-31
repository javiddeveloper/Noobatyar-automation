'use client';

import { useRouter } from 'next/navigation';
import Icon, { type IconName } from './Icon';

export interface ToolbarAction {
  icon: IconName;
  label: string;
  onClick: () => void;
}

interface ToolbarProps {
  title: string;
  /** Extra buttons rendered beside the back button. */
  actions?: ToolbarAction[];
  /** Replaces the default `router.back()`. */
  onBack?: () => void;
  /** Drops the back button entirely (root screens). */
  hideBack?: boolean;
}

/**
 * The shared top bar.
 *
 * `.toolbar` is `direction: ltr` on purpose (see globals.css) so the slots stay
 * where the CSS puts them regardless of content; the back control therefore
 * lands on the right, which is the reading-start side in this RTL app. The
 * left slot is intentionally left as a spacer — the floating `.theme-toggle`
 * is fixed there at z-index 200 and would swallow clicks on anything below it.
 */
export default function Toolbar({ title, actions = [], onBack, hideBack = false }: ToolbarProps) {
  const router = useRouter();

  return (
    <div className="toolbar">
      <div className="toolbar-placeholder" />
      <h1 className="toolbar-title">{title}</h1>

      <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
        {actions.map((action) => (
          <button
            key={action.label}
            className="toolbar-back"
            aria-label={action.label}
            title={action.label}
            onClick={action.onClick}
          >
            <Icon name={action.icon} size={20} />
          </button>
        ))}

        {hideBack ? (
          actions.length === 0 && <div className="toolbar-placeholder" />
        ) : (
          <button
            className="toolbar-back"
            aria-label="بازگشت"
            title="بازگشت"
            onClick={onBack ?? (() => router.back())}
          >
            <Icon name="back" size={20} />
          </button>
        )}
      </div>
    </div>
  );
}
