function StatusIndicator({ label, detail, status = 'neutral' }) {
  return (
    <div className={`status-indicator status-${status}`}>
      <span className="status-light" aria-hidden="true" />
      <span className="status-copy">
        <strong>{label}</strong>
        {detail && <span>{detail}</span>}
      </span>
    </div>
  )
}

export default StatusIndicator
