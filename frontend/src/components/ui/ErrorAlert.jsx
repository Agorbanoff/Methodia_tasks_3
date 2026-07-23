function ErrorAlert({ error, title, onRetry, onDismiss }) {
  if (!error) {
    return null
  }

  const displayTitle = title || error.title || 'Request failed'
  const description = error.description || error.message || 'Something went wrong while processing the request.'
  const timestamp = error.requestedAt ? new Date(error.requestedAt).toLocaleString('bg-BG') : null
  const details = sanitizeDetails(error.technicalDetails)

  return (
    <div className="alert alert-error detailed-error" role="alert">
      <div className="detailed-error-content">
        <strong>{displayTitle}</strong>
        <p>{description}</p>

        <div className="error-meta">
          {timestamp && <span>Failed at {timestamp}</span>}
          {error.endpoint && <span>{error.endpoint}</span>}
        </div>

        {details && (
          <details>
            <summary>Technical details</summary>
            <pre>{details}</pre>
          </details>
        )}
      </div>

      <div className="error-actions">
        {onRetry && (
          <button className="ghost-button" type="button" onClick={onRetry}>
            Retry
          </button>
        )}
        {onDismiss && (
          <button className="ghost-button alert-close" type="button" onClick={onDismiss} aria-label="Dismiss">
            ×
          </button>
        )}
      </div>
    </div>
  )
}

function sanitizeDetails(details) {
  if (!details) {
    return null
  }

  return JSON.stringify(
    details,
    (key, value) => {
      if (/token|secret|password|authorization|cookie/i.test(key)) {
        return '[redacted]'
      }
      return value
    },
    2,
  )
}

export default ErrorAlert
