function ToastStack({ toasts, onDismiss }) {
  return (
    <div className="toast-region" role="region" aria-label="Notifications">
      <div className="toast-stack" aria-live="polite" aria-relevant="additions removals">
        {toasts.map((toast) => (
          <div className={`toast toast-${toast.type || 'info'}`} key={toast.id}>
            <div>
              <strong>{toast.title}</strong>
              {toast.description && <p>{toast.description}</p>}
            </div>
            <button type="button" onClick={() => onDismiss(toast.id)} aria-label="Dismiss notification">
              ×
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}

export default ToastStack
