function FormField({ label, helperText, children }) {
  return (
    <label className="form-field">
      <span>{label}</span>
      {children}
      {helperText && <small>{helperText}</small>}
    </label>
  )
}

export default FormField
