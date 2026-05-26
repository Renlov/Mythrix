// Минимальные типы Telegram WebApp SDK (telegram-web-app.js).
interface TelegramWebApp {
  initData: string;
  ready: () => void;
  expand: () => void;
  colorScheme: "light" | "dark";
  themeParams: Record<string, string>;
}

interface Window {
  Telegram?: { WebApp?: TelegramWebApp };
}
