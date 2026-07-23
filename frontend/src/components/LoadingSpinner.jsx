function LoadingSpinner({ label = 'Loading' }) {
  return (
    <span className="spinner-wrap" aria-label={label}>
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </span>
  )
}

export default LoadingSpinner
