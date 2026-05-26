// Минимальные типы Telegram WebApp SDK (telegram-web-app.js).
interface TelegramWebApp {
  initData: string;
  ready: () => void;
  expand: () => void;
  colorScheme: "light" | "dark";
  themeParams: Record<string, string>;
  showConfirm?: (message: string, callback: (confirmed: boolean) => void) => void;
}

interface Window {
  Telegram?: { WebApp?: TelegramWebApp };
}
