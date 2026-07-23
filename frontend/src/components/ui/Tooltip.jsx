function Tooltip({ children, text }) {
  return (
    <span className="tooltip">
      {children}
      <span className="tooltip-content" role="tooltip">
        {text}
      </span>
    </span>
  )
}

export default Tooltip
