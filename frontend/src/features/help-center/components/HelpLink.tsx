import { Info } from 'lucide-react';
import { Link } from 'react-router-dom';

interface HelpLinkProps {
  to: string;
  label?: string;
  className?: string;
  newTab?: boolean;
}

export const HelpLink = ({
  to,
  label,
  className = '',
  newTab = false,
}: HelpLinkProps) => {
  return (
    <Link
      to={to}
      aria-label={label ?? 'Xem hướng dẫn liên quan'}
      className={`inline-flex items-center gap-1.5 text-sm font-medium text-blue-700 transition-colors hover:text-blue-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 ${className}`}
      target={newTab ? '_blank' : undefined}
      rel={newTab ? 'noopener noreferrer' : undefined}
    >
      <Info aria-hidden="true" className="h-4 w-4 shrink-0" />
      {label ? <span>{label}</span> : <span className="sr-only">Xem hướng dẫn liên quan</span>}
    </Link>
  );
};
