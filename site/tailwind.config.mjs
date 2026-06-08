/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{astro,html,js,jsx,md,mdx,ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        bg: {
          primary: '#0a0a0a',
          secondary: '#121214',
          card: '#1a1a1d',
          elevated: '#222227',
        },
        accent: {
          DEFAULT: '#6366f1',
          hover: '#818cf8',
          soft: '#a5b4fc',
          deep: '#4338ca',
        },
        // segunda cor de marca para gradientes (violeta) e terceira (ciano)
        violet: { DEFAULT: '#8b5cf6' },
        cyan: { DEFAULT: '#22d3ee' },
        // cores por locutor (diarização) — mesmas do app
        speaker: {
          1: '#6366f1',
          2: '#f97316',
          3: '#10b981',
          4: '#ec4899',
          5: '#06b6d4',
        },
        success: '#10b981',
        warning: '#f59e0b',
        danger: '#ef4444',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
      maxWidth: {
        prose: '70ch',
      },
      boxShadow: {
        glow: '0 0 0 1px rgba(99,102,241,0.25), 0 8px 40px -8px rgba(99,102,241,0.45)',
        'glow-lg': '0 0 0 1px rgba(99,102,241,0.3), 0 20px 70px -10px rgba(99,102,241,0.55)',
        card: '0 1px 0 0 rgba(255,255,255,0.04) inset, 0 8px 30px -12px rgba(0,0,0,0.8)',
      },
      backgroundImage: {
        'brand-gradient': 'linear-gradient(110deg, #818cf8 0%, #6366f1 35%, #8b5cf6 70%, #22d3ee 130%)',
        'radial-fade': 'radial-gradient(60% 60% at 50% 0%, rgba(99,102,241,0.18) 0%, rgba(10,10,10,0) 70%)',
        grid: 'linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px)',
      },
      keyframes: {
        'reveal-up': {
          from: { opacity: '0', transform: 'translateY(26px)' },
          to: { opacity: '1', transform: 'none' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-10px)' },
        },
        shimmer: {
          '0%': { backgroundPosition: '200% center' },
          '100%': { backgroundPosition: '-200% center' },
        },
        'pulse-rec': {
          '0%, 100%': { opacity: '1', transform: 'scale(1)' },
          '50%': { opacity: '0.45', transform: 'scale(0.85)' },
        },
        'border-spin': {
          to: { '--angle': '360deg' },
        },
      },
      animation: {
        'reveal-up': 'reveal-up 0.7s cubic-bezier(0.22, 1, 0.36, 1) both',
        float: 'float 6s ease-in-out infinite',
        shimmer: 'shimmer 6s linear infinite',
        'pulse-rec': 'pulse-rec 1.4s ease-in-out infinite',
      },
    },
  },
  plugins: [require('@tailwindcss/typography')],
};
