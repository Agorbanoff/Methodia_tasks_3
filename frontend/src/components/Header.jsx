import IconButton from './ui/IconButton.jsx'
function Header({ selectedPeriod, theme, onToggleTheme }) {
  return (
    <header className="app-header">
      <nav className="top-nav" aria-label="Main navigation">
        <div className="brand-cluster">
          <div className="brand-mark" aria-hidden="true">
            MB
          </div>
          <div className="nav-copy">
            <h1>Mini Billing</h1>
            <p>Utility invoicing control center</p>
          </div>
        </div>

        <div className="period-chip">
          <span>Selected period</span>
          <strong>{selectedPeriod}</strong>
        </div>

        <div className="shell-actions">
          <div className="user-pill">
            <span className="status-dot" aria-hidden="true" />
            <p className="user-name">Billing Administrator</p>
          </div>
          <IconButton label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`} onClick={onToggleTheme}>
            {theme === 'dark' ? '☀' : '☾'}
          </IconButton>
        </div>
      </nav>
    </header>
  )
}

export default Header
