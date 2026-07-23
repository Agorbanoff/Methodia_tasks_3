function Button({ children, className = '', variant = 'primary', size = 'md', ...props }) {
  return (
    <button className={`button button-${variant} button-${size} ${className}`.trim()} {...props}>
      {children}
    </button>
  )
}

export default Button
