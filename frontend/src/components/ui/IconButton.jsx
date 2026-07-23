function IconButton({ children, className = '', label, variant = 'ghost', ...props }) {
  return (
    <button
      className={`icon-button icon-button-${variant} ${className}`.trim()}
      type="button"
      aria-label={label}
      {...props}
    >
      {children}
    </button>
  )
}

export default IconButton
