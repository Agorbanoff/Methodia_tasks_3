import IconButton from './IconButton.jsx'

function Alert({ type = 'info', title, children, onDismiss }) {
  if (!children && !title) {
    return null
  }

  return (
    <div className={`alert alert-${type}`} role={type === 'error' ? 'alert' : 'status'}>
      <div>
        {title && <strong>{title}</strong>}
        {children && <p>{children}</p>}
      </div>
      {onDismiss && (
        <IconButton className="alert-close" label="Dismiss" onClick={onDismiss}>
          ×
        </IconButton>
      )}
    </div>
  )
}

export default Alert
