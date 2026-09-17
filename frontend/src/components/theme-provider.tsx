import * as React from "react"
import {
  ThemeProvider as NextThemesProvider,
  type ThemeProviderProps,
} from "next-themes"

/**
 * next-themes injects an inline <script> to avoid theme FOUC. On React 19 CSR
 * that script is never executed and React logs a console error. FOUC for this
 * Vite app is handled by the blocking script in index.html instead.
 */
const SCRIPT_TAG_WARNING = "Encountered a script tag while rendering React component"

if (typeof window !== "undefined") {
  const originalConsoleError = console.error
  console.error = (...args: unknown[]) => {
    const message = args
      .map((arg) => {
        if (typeof arg === "string") return arg
        if (arg instanceof Error) return arg.message
        return ""
      })
      .join(" ")
    if (message.includes(SCRIPT_TAG_WARNING)) {
      return
    }
    originalConsoleError.apply(console, args)
  }
}

export function ThemeProvider({ children, ...props }: ThemeProviderProps) {
  return <NextThemesProvider {...props}>{children}</NextThemesProvider>
}
