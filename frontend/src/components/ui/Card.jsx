function Card({ as: Component = 'section', children, className = '', elevated = false, ...props }) {
  return (
    <Component className={`card ${elevated ? 'card-elevated' : ''} ${className}`.trim()} {...props}>
      {children}
    </Component>
  )
}

export default Card
