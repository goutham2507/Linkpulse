import { safeHref } from "../util/safeUrl";

// Renders a link only when the URL is http(s); otherwise plain text.
export default function ExternalLink({ url }: { url: string }) {
  const href = safeHref(url);
  if (!href) return <span>{url}</span>;
  return (
    <a href={href} target="_blank" rel="noopener noreferrer">
      {url}
    </a>
  );
}
