/** Keeps the `dark` class in sync with the browser/OS color scheme. */
export function applySystemTheme() {
  const root = document.documentElement
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  root.classList.toggle('dark', prefersDark)
  root.style.colorScheme = prefersDark ? 'dark' : 'light'

  const themeMeta = document.querySelector('meta[name="theme-color"]')
  if (themeMeta) {
    themeMeta.setAttribute('content', prefersDark ? '#09090b' : '#ffffff')
  }
}

export function watchSystemTheme() {
  applySystemTheme()
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  const onChange = () => applySystemTheme()
  media.addEventListener('change', onChange)
  return () => media.removeEventListener('change', onChange)
}
