/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        pk: {
          blue: "#004785",
          accent: "#0072ce",
          light: "#e8f1f8",
        },
      },
    },
  },
  plugins: [],
}
