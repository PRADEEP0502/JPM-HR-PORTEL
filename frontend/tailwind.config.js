/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: { sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'] },
      colors: {
        canvas: '#0e0f11',
        panel: '#16171a',
        card: '#1c1d21',
        raised: '#24252a',
        line: '#2b2c31',
        ink: { DEFAULT: '#f2f2ef', 2: '#b9b9b2', 3: '#8a8a85' },
        good: { DEFAULT: '#22a559', soft: 'rgba(34,165,89,0.14)', card: '#25924f' },
        info: { DEFAULT: '#3d74e0', soft: 'rgba(61,116,224,0.16)', card: '#3f68cf' },
        warn: { DEFAULT: '#e08a2e', soft: 'rgba(224,138,46,0.16)', card: '#b65d22' },
        bad: { DEFAULT: '#e05555', soft: 'rgba(224,85,85,0.15)' },
        violet: { DEFAULT: '#9085e9', soft: 'rgba(144,133,233,0.16)' },
        aqua: { DEFAULT: '#1fb488', soft: 'rgba(31,180,136,0.15)' },
      },
      boxShadow: { card: '0 1px 0 rgba(255,255,255,0.03) inset, 0 8px 24px -12px rgba(0,0,0,0.6)' },
      keyframes: {
        'fade-up': { from: { opacity: '0', transform: 'translateY(6px)' }, to: { opacity: '1', transform: 'none' } },
      },
      animation: { 'fade-up': 'fade-up .35s ease-out both' },
    },
  },
  plugins: [],
};
