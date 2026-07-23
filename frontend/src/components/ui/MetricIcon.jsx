function MetricIcon({ name }) {
  return (
    <span className="metric-icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" role="img">
        {icons[name] || icons.document}
      </svg>
    </span>
  )
}

const icons = {
  document: (
    <>
      <path d="M7 3.75h7.2L18 7.55v12.7H7z" />
      <path d="M14 3.9v4h4" />
      <path d="M9.5 11h5M9.5 14h5M9.5 17h3" />
    </>
  ),
  amount: (
    <>
      <path d="M5 7.5h14v10H5z" />
      <path d="M8 10.5h4.5M8 13.5h8" />
      <path d="M16.5 10.25v3.5" />
    </>
  ),
  calendar: (
    <>
      <path d="M5 6.5h14v12H5z" />
      <path d="M5 10h14M8 4.5v3M16 4.5v3" />
      <path d="M8.5 13h2M13.5 13h2M8.5 16h2" />
    </>
  ),
  hash: (
    <>
      <path d="M9 4.5 7.5 19.5M16.5 4.5 15 19.5" />
      <path d="M5 9h14M4.5 15h14" />
    </>
  ),
  lines: (
    <>
      <path d="M5.5 7h13M5.5 12h13M5.5 17h13" />
      <path d="M3.5 7h.1M3.5 12h.1M3.5 17h.1" />
    </>
  ),
  average: (
    <>
      <path d="M5 16.5 10 9l3.5 4 5.5-7.5" />
      <path d="M5 19h14" />
    </>
  ),
}

export default MetricIcon
