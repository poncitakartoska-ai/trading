#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MOEX Pulse AI • Quant 2.0 Desktop Terminal (Windows PC .exe Edition)
Реальный мониторинг Мосбиржи (MOEX ISS API, режим TQBR), 7-факторный движок Quant 2.0,
живая лента новостей, калькулятор риска в лотах, переключение Светлой/Темной темы
и мгновенное открытие графиков TradingView в один клик.
"""

import json
import math
import threading
import time
import tkinter as tk
from tkinter import ttk, messagebox
import urllib.request
import webbrowser
from datetime import datetime, timedelta

WATCHED_TICKERS = [
    ("SBER", "Сбербанк", 1, 0.01),
    ("GAZP", "Газпром", 10, 0.01),
    ("LKOH", "ЛУКОЙЛ", 1, 0.5),
    ("YDEX", "Яндекс", 1, 0.5),
    ("T", "Т-Технологии", 1, 0.2),
    ("ROSN", "Роснефть", 1, 0.05),
    ("NVTK", "НОВАТЭК", 1, 0.2),
    ("GMKN", "Норникель", 10, 0.02),
    ("SNGS", "Сургутнефтегаз", 100, 0.005),
    ("TATN", "Татнефть", 1, 0.1),
    ("MTSS", "МТС", 10, 0.05),
    ("CHMF", "Северсталь", 1, 0.2),
    ("PLZL", "Полюс", 1, 0.5),
    ("ALRS", "АЛРОСА", 10, 0.01),
    ("MOEX", "Мосбиржа", 10, 0.01),
]

BULLISH_WORDS = [
    "дивиденд", "прибыл", "рост", "рекорд", "выкуп", "байбэк", "повысил",
    "одобрил", "рекомендовал", "отчет", "выручк", "инвестиц", "лидер"
]
BEARISH_WORDS = [
    "убыток", "падени", "снижен", "санкц", "отказ", "штраф", "долг",
    "инфляц", "ставк", "дефицит", "риск", "коррекц", "обвал"
]


def tradingview_url(ticker: str) -> str:
    return f"https://ru.tradingview.com/chart/?symbol=MOEX:{ticker}"


def calc_ema(values, period):
    if not values:
        return 0.0
    k = 2.0 / (period + 1.0)
    ema = values[0]
    for v in values[1:]:
        ema = v * k + ema * (1.0 - k)
    return ema


def calc_rsi(closes, period=14):
    if len(closes) <= period:
        return 50.0
    gains = 0.0
    losses = 0.0
    for i in range(1, period + 1):
        diff = closes[i] - closes[i - 1]
        if diff >= 0:
            gains += diff
        else:
            losses -= diff
    avg_gain = gains / period
    avg_loss = losses / period
    for i in range(period + 1, len(closes)):
        diff = closes[i] - closes[i - 1]
        gain = max(diff, 0.0)
        loss = max(-diff, 0.0)
        avg_gain = (avg_gain * (period - 1) + gain) / period
        avg_loss = (avg_loss * (period - 1) + loss) / period
    if avg_loss < 1e-9:
        return 100.0
    rs = avg_gain / avg_loss
    return 100.0 - (100.0 / (1.0 + rs))


def calc_atr(candles, period=14):
    if len(candles) < 2:
        return 0.0
    trs = []
    for i in range(1, len(candles)):
        h = candles[i]["high"]
        l = candles[i]["low"]
        pc = candles[i - 1]["close"]
        tr = max(h - l, abs(h - pc), abs(l - pc))
        trs.append(tr)
    if len(trs) <= period:
        return sum(trs) / max(1, len(trs))
    atr = sum(trs[:period]) / period
    for tr in trs[period:]:
        atr = (atr * (period - 1) + tr) / period
    return atr


def calc_vwap(candles, lookback=30):
    window = candles[-lookback:] if len(candles) >= lookback else candles
    pv = 0.0
    vol = 0.0
    for c in window:
        tp = (c["high"] + c["low"] + c["close"]) / 3.0
        v = max(c["volume"], 1.0)
        pv += tp * v
        vol += v
    return pv / vol if vol > 0 else (window[-1]["close"] if window else 0.0)


def calc_bollinger_pct_b(closes, period=20):
    window = closes[-period:] if len(closes) >= period else closes
    if not window:
        return 0.5
    mean = sum(window) / len(window)
    variance = sum((x - mean) ** 2 for x in window) / len(window)
    std = max(math.sqrt(variance), 1e-8)
    upper = mean + 2.0 * std
    lower = mean - 2.0 * std
    width = max(upper - lower, 1e-8)
    return max(0.0, min(1.2, (closes[-1] - lower) / width))


def round_step(price, step):
    if step <= 0:
        return round(price, 2)
    return round(price / step) * step


def fmt_price(price, step):
    if step >= 1.0:
        return f"{price:.0f}"
    elif step >= 0.1:
        return f"{price:.1f}"
    elif step >= 0.01:
        return f"{price:.2f}"
    else:
        return f"{price:.3f}"


class MoexPulseDesktopApp:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("MOEX Pulse AI • Quant 2.0 Desktop Terminal (PC .EXE)")
        self.root.geometry("1280x800")
        self.root.minsize(1060, 680)

        self.is_dark = True
        self.strategy_profile = tk.StringVar(value="CONSERVATIVE")
        self.candle_interval = tk.IntVar(value=10)
        self.deposit_var = tk.StringVar(value="150000")
        self.risk_pct_var = tk.StringVar(value="1.0")
        self.filter_var = tk.StringVar(value="ALL")

        self.signals_data = []
        self.news_data = []
        self.history_data = []
        self.selected_signal = None
        self.running = True

        self._build_ui()
        self._apply_theme()

        threading.Thread(target=self._poll_loop, daemon=True).start()

    def _get_palette(self):
        if self.is_dark:
            return {
                "bg": "#090D16",
                "surface": "#111827",
                "elevated": "#1E293B",
                "border": "#26334D",
                "text": "#F8FAFC",
                "subtext": "#94A3B8",
                "long": "#10B981",
                "short": "#F43F5E",
                "wait": "#F59E0B",
                "cyan": "#38BDF8",
            }
        else:
            return {
                "bg": "#F4F7FB",
                "surface": "#FFFFFF",
                "elevated": "#EDF2F9",
                "border": "#CBD5E1",
                "text": "#0F172A",
                "subtext": "#475569",
                "long": "#059669",
                "short": "#E11D48",
                "wait": "#D97706",
                "cyan": "#0284C7",
            }

    def _build_ui(self):
        # Top Header Bar
        self.header_frame = tk.Frame(self.root, padx=16, pady=12)
        self.header_frame.pack(fill=tk.X)

        self.title_lbl = tk.Label(
            self.header_frame,
            text="MOEX PULSE AI • QUANT 2.0 DESKTOP (.EXE)",
            font=("Consolas", 15, "bold")
        )
        self.title_lbl.pack(side=tk.LEFT)

        self.status_lbl = tk.Label(
            self.header_frame,
            text="Загрузка котировок MOEX ISS API...",
            font=("Consolas", 10)
        )
        self.status_lbl.pack(side=tk.LEFT, padx=18)

        self.theme_btn = tk.Button(
            self.header_frame,
            text="☀ Светлая / 🌙 Темная тема",
            font=("Segoe UI", 10, "bold"),
            command=self._toggle_theme,
            relief=tk.FLAT,
            padx=12,
            pady=5
        )
        self.theme_btn.pack(side=tk.RIGHT, padx=4)

        self.refresh_btn = tk.Button(
            self.header_frame,
            text="⟳ Обновить сейчас",
            font=("Segoe UI", 10, "bold"),
            command=self._manual_refresh,
            relief=tk.FLAT,
            padx=12,
            pady=5
        )
        self.refresh_btn.pack(side=tk.RIGHT, padx=4)

        # Control Bar (Filter + Profile + Timeframe)
        self.ctrl_frame = tk.Frame(self.root, padx=16, pady=6)
        self.ctrl_frame.pack(fill=tk.X)

        tk.Label(self.ctrl_frame, text="Фильтр:", font=("Segoe UI", 10, "bold")).pack(side=tk.LEFT, padx=(0, 6))
        for code, title in [("ALL", "Все (15)"), ("ACTIVE", "Активные"), ("LONG", "LONG"), ("SHORT", "SHORT"), ("WAIT", "WAIT")]:
            rb = tk.Radiobutton(
                self.ctrl_frame,
                text=title,
                variable=self.filter_var,
                value=code,
                command=self._populate_signals_table,
                indicatoron=False,
                font=("Consolas", 10, "bold"),
                padx=10,
                pady=4,
                relief=tk.FLAT
            )
            rb.pack(side=tk.LEFT, padx=3)

        tk.Label(self.ctrl_frame, text="   Профиль Quant 2.0:", font=("Segoe UI", 10, "bold")).pack(side=tk.LEFT, padx=(12, 6))
        profile_cb = ttk.Combobox(
            self.ctrl_frame,
            textvariable=self.strategy_profile,
            values=["CONSERVATIVE", "BALANCED", "AGGRESSIVE"],
            state="readonly",
            width=16
        )
        profile_cb.pack(side=tk.LEFT, padx=4)
        profile_cb.bind("<<ComboboxSelected>>", lambda _e: self._manual_refresh())

        # Main PanedWindow: Left = Signals Table + News Tab, Right = TradingView & Lot Calculator
        self.main_pane = tk.PanedWindow(self.root, orient=tk.HORIZONTAL, sashwidth=6)
        self.main_pane.pack(fill=tk.BOTH, expand=True, padx=16, pady=8)

        self.left_notebook = ttk.Notebook(self.main_pane)
        self.main_pane.add(self.left_notebook, width=820)

        # Tab 1: Live Signals Table
        self.signals_tab = tk.Frame(self.left_notebook)
        self.left_notebook.add(self.signals_tab, text=" 📈 Живые сигналы Quant 2.0 (15 акций) ")

        columns = ("ticker", "name", "price", "chg", "signal", "conf", "entry", "tp", "sl", "rsi", "vwap", "tv")
        self.tree = ttk.Treeview(self.signals_tab, columns=columns, show="headings", height=20)
        headings = {
            "ticker": ("Тикер", 65),
            "name": ("Эмитент", 115),
            "price": ("Цена ₽", 80),
            "chg": ("Изм %", 68),
            "signal": ("Сигнал", 85),
            "conf": ("Конфл.", 72),
            "entry": ("Вход (Entry)", 88),
            "tp": ("Тейк (TP)", 88),
            "sl": ("Стоп (SL)", 88),
            "rsi": ("RSI(14)", 62),
            "vwap": ("VWAP ₽", 80),
            "tv": ("TradingView", 110),
        }
        for col, (text, w) in headings.items():
            self.tree.heading(col, text=text)
            self.tree.column(col, width=w, anchor=tk.CENTER if col != "name" else tk.W)

        vsb = ttk.Scrollbar(self.signals_tab, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=vsb.set)
        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        vsb.pack(side=tk.RIGHT, fill=tk.Y)

        self.tree.bind("<<TreeviewSelect>>", self._on_row_select)
        self.tree.bind("<Double-1>", self._on_row_double_click)

        # Tab 2: Live News & Sentiment Feed
        self.news_tab = tk.Frame(self.left_notebook)
        self.left_notebook.add(self.news_tab, text=" 📰 Лента новостей и сентимент РФ ")

        news_cols = ("time", "source", "sentiment", "tickers", "title")
        self.news_tree = ttk.Treeview(self.news_tab, columns=news_cols, show="headings", height=18)
        self.news_tree.heading("time", text="Время")
        self.news_tree.column("time", width=95, anchor=tk.CENTER)
        self.news_tree.heading("source", text="Источник")
        self.news_tree.column("source", width=110, anchor=tk.CENTER)
        self.news_tree.heading("sentiment", text="Сентимент")
        self.news_tree.column("sentiment", width=110, anchor=tk.CENTER)
        self.news_tree.heading("tickers", text="Тикеры (TradingView)")
        self.news_tree.column("tickers", width=130, anchor=tk.CENTER)
        self.news_tree.heading("title", text="Заголовок новости (двойной клик — открыть график TradingView)")
        self.news_tree.column("title", width=420, anchor=tk.W)
        self.news_tree.pack(fill=tk.BOTH, expand=True)
        self.news_tree.bind("<Double-1>", self._on_news_double_click)

        # Right Inspector Panel: Selected Stock Details + Immediate TradingView Button + Risk Calculator
        self.right_frame = tk.Frame(self.main_pane, padx=14, pady=12)
        self.main_pane.add(self.right_frame, width=410)

        self.detail_title = tk.Label(
            self.right_frame,
            text="Выберите акцию в таблице",
            font=("Consolas", 14, "bold"),
            anchor="w"
        )
        self.detail_title.pack(fill=tk.X)

        self.detail_badge = tk.Label(
            self.right_frame,
            text="Ожидание выбора",
            font=("Consolas", 11, "bold"),
            anchor="w",
            pady=4
        )
        self.detail_badge.pack(fill=tk.X)

        # Prominent TradingView Button
        self.tv_btn = tk.Button(
            self.right_frame,
            text="📈 Открыть график в TradingView ↗",
            font=("Segoe UI", 11, "bold"),
            command=self._open_selected_tradingview,
            relief=tk.FLAT,
            padx=12,
            pady=8
        )
        self.tv_btn.pack(fill=tk.X, pady=(6, 10))

        self.detail_text = tk.Text(
            self.right_frame,
            height=14,
            font=("Consolas", 10),
            wrap=tk.WORD,
            relief=tk.FLAT,
            padx=10,
            pady=10
        )
        self.detail_text.pack(fill=tk.X, pady=4)

        # Position Risk Calculator
        self.calc_frame = tk.LabelFrame(
            self.right_frame,
            text=" Калькулятор риска и лотов ",
            font=("Consolas", 10, "bold"),
            padx=10,
            pady=10
        )
        self.calc_frame.pack(fill=tk.X, pady=10)

        row1 = tk.Frame(self.calc_frame)
        row1.pack(fill=tk.X, pady=3)
        tk.Label(row1, text="Депозит (₽):", font=("Segoe UI", 10)).pack(side=tk.LEFT)
        dep_entry = tk.Entry(row1, textvariable=self.deposit_var, font=("Consolas", 10), width=14)
        dep_entry.pack(side=tk.RIGHT)
        dep_entry.bind("<KeyRelease>", lambda _e: self._update_inspector())

        row2 = tk.Frame(self.calc_frame)
        row2.pack(fill=tk.X, pady=3)
        tk.Label(row2, text="Риск на сделку (%):", font=("Segoe UI", 10)).pack(side=tk.LEFT)
        risk_entry = tk.Entry(row2, textvariable=self.risk_pct_var, font=("Consolas", 10), width=14)
        risk_entry.pack(side=tk.RIGHT)
        risk_entry.bind("<KeyRelease>", lambda _e: self._update_inspector())

        self.calc_result_lbl = tk.Label(
            self.calc_frame,
            text="Выберите бумагу для расчета лотов",
            font=("Consolas", 10, "bold"),
            justify=tk.LEFT,
            anchor="w",
            pady=6
        )
        self.calc_result_lbl.pack(fill=tk.X)

    def _toggle_theme(self):
        self.is_dark = not self.is_dark
        self._apply_theme()

    def _apply_theme(self):
        p = self._get_palette()
        self.root.configure(bg=p["bg"])
        self.header_frame.configure(bg=p["surface"])
        self.title_lbl.configure(bg=p["surface"], fg=p["text"])
        self.status_lbl.configure(bg=p["surface"], fg=p["cyan"])
        self.theme_btn.configure(bg=p["elevated"], fg=p["text"], activebackground=p["cyan"])
        self.refresh_btn.configure(bg=p["long"], fg="#FFFFFF", activebackground=p["long"])

        self.ctrl_frame.configure(bg=p["bg"])
        for child in self.ctrl_frame.winfo_children():
            if isinstance(child, tk.Label):
                child.configure(bg=p["bg"], fg=p["subtext"])
            elif isinstance(child, tk.Radiobutton):
                child.configure(
                    bg=p["surface"],
                    fg=p["text"],
                    selectcolor=p["elevated"],
                    activebackground=p["elevated"]
                )

        self.right_frame.configure(bg=p["surface"])
        self.detail_title.configure(bg=p["surface"], fg=p["text"])
        self.detail_badge.configure(bg=p["surface"], fg=p["cyan"])
        self.tv_btn.configure(bg=p["cyan"], fg="#090D16", activebackground=p["long"])
        self.detail_text.configure(bg=p["elevated"], fg=p["text"], insertbackground=p["text"])
        self.calc_frame.configure(bg=p["surface"], fg=p["cyan"])
        self.calc_result_lbl.configure(bg=p["surface"], fg=p["long"])

        style = ttk.Style()
        style.theme_use("clam")
        style.configure(
            "Treeview",
            background=p["surface"],
            foreground=p["text"],
            fieldbackground=p["surface"],
            rowheight=28,
            font=("Consolas", 10)
        )
        style.configure(
            "Treeview.Heading",
            background=p["elevated"],
            foreground=p["text"],
            font=("Consolas", 10, "bold")
        )
        style.map("Treeview", background=[("selected", p["elevated"])])

    def _manual_refresh(self):
        threading.Thread(target=self._fetch_all_once, daemon=True).start()

    def _poll_loop(self):
        while self.running:
            self._fetch_all_once()
            time.sleep(10)

    def _fetch_json(self, url: str):
        req = urllib.request.Request(
            url,
            headers={"User-Agent": "MOEX-Pulse-AI-Desktop/2.0"}
        )
        with urllib.request.urlopen(req, timeout=12) as resp:
            return json.loads(resp.read().decode("utf-8", errors="ignore"))

    def _fetch_all_once(self):
        try:
            # 1. Fetch MOEX site news
            news_items = []
            try:
                news_json = self._fetch_json("https://iss.moex.com/iss/sitenews.json?iss.meta=off")
                sitenews = news_json.get("sitenews", {})
                cols = sitenews.get("columns", [])
                rows = sitenews.get("data", [])
                idx_title = cols.index("title") if "title" in cols else -1
                idx_date = cols.index("modified_at") if "modified_at" in cols else -1
                for r in rows[:20]:
                    title = str(r[idx_title]) if idx_title >= 0 else ""
                    dt = str(r[idx_date])[11:16] if idx_date >= 0 else ""
                    low = title.lower()
                    score = 0.0
                    for w in BULLISH_WORDS:
                        if w in low:
                            score += 0.25
                    for w in BEARISH_WORDS:
                        if w in low:
                            score -= 0.25
                    sent = "🟢 ПОЗИТИВ" if score >= 0.15 else ("🔴 НЕГАТИВ" if score <= -0.15 else "⚪ НЕЙТР.")
                    related = [t for t, n, _, _ in WATCHED_TICKERS if t.lower() in low or n.lower() in low]
                    if not related:
                        related = ["MOEX"]
                    news_items.append({
                        "time": dt,
                        "source": "MOEX ISS",
                        "sentiment": f"{sent} ({score:+.2f})",
                        "score": score,
                        "tickers": ", ".join(related),
                        "primary_ticker": related[0],
                        "title": title
                    })
            except Exception:
                pass

            # 2. Fetch TQBR board snapshot
            sec_list = ",".join(t for t, _, _, _ in WATCHED_TICKERS)
            board_url = (
                f"https://iss.moex.com/iss/engines/stock/markets/shares/boards/TQBR/securities.json"
                f"?securities={sec_list}&iss.meta=off&iss.only=securities,marketdata"
            )
            board_json = self._fetch_json(board_url)
            md = board_json.get("marketdata", {})
            md_cols = md.get("columns", [])
            md_rows = md.get("data", [])

            quotes = {}
            for r in md_rows:
                row_dict = dict(zip(md_cols, r))
                secid = row_dict.get("SECID")
                last = row_dict.get("LAST") or row_dict.get("MARKETPRICE") or row_dict.get("LCURRENTPRICE") or 0.0
                chg = row_dict.get("LASTTOPREVPRICE") or 0.0
                val = row_dict.get("VALTODAY") or 0.0
                if secid and float(last or 0.0) > 0:
                    quotes[secid] = (float(last), float(chg), float(val))

            from_date = (datetime.now() - timedelta(days=14)).strftime("%Y-%m-%d")
            interval = self.candle_interval.get()
            profile = self.strategy_profile.get()

            computed_signals = []
            for ticker, name, lot, step in WATCHED_TICKERS:
                last_price, chg_pct, turnover = quotes.get(ticker, (0.0, 0.0, 0.0))
                candles = []
                try:
                    c_url = (
                        f"https://iss.moex.com/iss/engines/stock/markets/shares/boards/TQBR/securities/"
                        f"{ticker}/candles.json?interval={interval}&from={from_date}&iss.meta=off"
                    )
                    c_json = self._fetch_json(c_url)
                    c_obj = c_json.get("candles", {})
                    c_cols = c_obj.get("columns", [])
                    c_rows = c_obj.get("data", [])
                    for cr in c_rows[-90:]:
                        cd = dict(zip(c_cols, cr))
                        cl = float(cd.get("close") or 0.0)
                        if cl > 0:
                            candles.append({
                                "open": float(cd.get("open") or cl),
                                "high": float(cd.get("high") or cl),
                                "low": float(cd.get("low") or cl),
                                "close": cl,
                                "volume": float(cd.get("volume") or 1.0),
                            })
                except Exception:
                    pass

                if not candles and last_price <= 0:
                    continue

                closes = [c["close"] for c in candles] if candles else [last_price]
                cur = last_price if last_price > 0 else closes[-1]
                ema20 = calc_ema(closes, 20)
                ema50 = calc_ema(closes, 50)
                rsi14 = calc_rsi(closes, 14)
                atr14 = max(calc_atr(candles, 14), cur * 0.005)
                vwap = calc_vwap(candles, 30) if candles else cur
                pct_b = calc_bollinger_pct_b(closes, 20)

                # Multi-factor Quant 2.0 confluence
                bull_pts = 0
                bear_pts = 0
                if cur > ema50 and ema20 >= ema50 * 0.999:
                    bull_pts += 1
                if cur < ema50 and ema20 <= ema50 * 1.001:
                    bear_pts += 1
                if cur >= vwap:
                    bull_pts += 1
                else:
                    bear_pts += 1
                if 40 <= rsi14 <= 64:
                    bull_pts += 1
                if 36 <= rsi14 <= 60:
                    bear_pts += 1
                if 0.20 <= pct_b <= 0.85:
                    bull_pts += 1
                if 0.15 <= pct_b <= 0.80:
                    bear_pts += 1
                if chg_pct > 0:
                    bull_pts += 1
                elif chg_pct < 0:
                    bear_pts += 1

                req_pts = 4 if profile == "CONSERVATIVE" else 3
                if bull_pts >= req_pts and cur > ema50 and pct_b <= 0.88 and rsi14 <= 66:
                    direction = "LONG"
                    sl = round_step(cur - 1.5 * atr14, step)
                    tp = round_step(cur + 3.0 * atr14, step)
                    conf = min(96, 64 + bull_pts * 6)
                    score = bull_pts + 2
                elif bear_pts >= req_pts and cur < ema50 and pct_b >= 0.12 and rsi14 >= 34:
                    direction = "SHORT"
                    sl = round_step(cur + 1.5 * atr14, step)
                    tp = round_step(cur - 3.0 * atr14, step)
                    conf = min(96, 64 + bear_pts * 6)
                    score = bear_pts + 2
                else:
                    direction = "WAIT"
                    sl = round_step(cur - 1.5 * atr14, step)
                    tp = round_step(cur + 3.0 * atr14, step)
                    conf = 52
                    score = max(bull_pts, bear_pts)

                computed_signals.append({
                    "ticker": ticker,
                    "name": name,
                    "lot": lot,
                    "step": step,
                    "price": cur,
                    "chg": chg_pct,
                    "turnover": turnover,
                    "direction": direction,
                    "conf": conf,
                    "score": min(7, score),
                    "entry": round_step(cur, step),
                    "tp": tp,
                    "sl": sl,
                    "ema20": ema20,
                    "ema50": ema50,
                    "rsi": rsi14,
                    "atr": atr14,
                    "vwap": vwap,
                    "pct_b": pct_b,
                    "tv_url": tradingview_url(ticker),
                })

            computed_signals.sort(key=lambda s: (0 if s["direction"] != "WAIT" else 1, -s["conf"]))
            self.signals_data = computed_signals
            self.news_data = news_items
            now_str = datetime.now().strftime("%H:%M:%S")

            self.root.after(0, lambda: self._update_ui_after_fetch(now_str))
        except Exception as e:
            self.root.after(0, lambda: self.status_lbl.configure(text=f"Ошибка сети: {e}"))

    def _update_ui_after_fetch(self, now_str: str):
        longs = sum(1 for s in self.signals_data if s["direction"] == "LONG")
        shorts = sum(1 for s in self.signals_data if s["direction"] == "SHORT")
        waits = sum(1 for s in self.signals_data if s["direction"] == "WAIT")
        self.status_lbl.configure(
            text=f"MOEX TQBR Live • Обновлено: {now_str} • LONG: {longs} | SHORT: {shorts} | WAIT: {waits}"
        )
        self._populate_signals_table()
        self._populate_news_table()

    def _populate_signals_table(self):
        flt = self.filter_var.get()
        for item in self.tree.get_children():
            self.tree.delete(item)

        for s in self.signals_data:
            d = s["direction"]
            if flt == "ACTIVE" and d == "WAIT":
                continue
            if flt in ("LONG", "SHORT", "WAIT") and d != flt:
                continue

            badge = "🟢 LONG" if d == "LONG" else ("🔴 SHORT" if d == "SHORT" else "⏳ WAIT")
            step = s["step"]
            self.tree.insert(
                "",
                tk.END,
                iid=s["ticker"],
                values=(
                    s["ticker"],
                    s["name"],
                    fmt_price(s["price"], step),
                    f"{s['chg']:+.2f}%",
                    badge,
                    f"{s['score']}/7 ({s['conf']}%)",
                    fmt_price(s["entry"], step),
                    fmt_price(s["tp"], step),
                    fmt_price(s["sl"], step),
                    f"{s['rsi']:.1f}",
                    fmt_price(s["vwap"], step),
                    "Открыть TV ↗",
                )
            )

        if self.selected_signal is None and self.signals_data:
            self.selected_signal = self.signals_data[0]
            self._update_inspector()

    def _populate_news_table(self):
        for item in self.news_tree.get_children():
            self.news_tree.delete(item)
        for idx, n in enumerate(self.news_data):
            self.news_tree.insert(
                "",
                tk.END,
                iid=f"news_{idx}",
                values=(n["time"], n["source"], n["sentiment"], n["tickers"], n["title"])
            )

    def _on_row_select(self, _event):
        sel = self.tree.selection()
        if not sel:
            return
        ticker = sel[0]
        for s in self.signals_data:
            if s["ticker"] == ticker:
                self.selected_signal = s
                self._update_inspector()
                break

    def _on_row_double_click(self, _event):
        self._open_selected_tradingview()

    def _on_news_double_click(self, _event):
        sel = self.news_tree.selection()
        if not sel:
            return
        idx = int(sel[0].replace("news_", ""))
        if 0 <= idx < len(self.news_data):
            ticker = self.news_data[idx]["primary_ticker"]
            webbrowser.open(tradingview_url(ticker))

    def _open_selected_tradingview(self):
        if self.selected_signal:
            webbrowser.open(self.selected_signal["tv_url"])

    def _update_inspector(self):
        s = self.selected_signal
        if not s:
            return
        step = s["step"]
        self.detail_title.configure(text=f"{s['ticker']} • {s['name']} ({fmt_price(s['price'], step)} ₽)")
        self.detail_badge.configure(
            text=f"Сигнал: {s['direction']} • Уверенность {s['conf']}% • Конфлюэнс {s['score']}/7"
        )

        self.detail_text.delete("1.0", tk.END)
        info = (
            f"Точка входа (Entry):  {fmt_price(s['entry'], step)} ₽\n"
            f"Тейк-профит (TP):     {fmt_price(s['tp'], step)} ₽\n"
            f"Стоп-лосс (SL):       {fmt_price(s['sl'], step)} ₽\n"
            f"──────────────────────────────────────\n"
            f"VWAP (институц.):     {fmt_price(s['vwap'], step)} ₽\n"
            f"EMA 20 / EMA 50:      {fmt_price(s['ema20'], step)} / {fmt_price(s['ema50'], step)} ₽\n"
            f"RSI (14):             {s['rsi']:.1f}\n"
            f"ATR (14):             {fmt_price(s['atr'], step)} ₽\n"
            f"Bollinger %B:         {s['pct_b'] * 100:.0f}%\n"
            f"Лотность бумаги:      {s['lot']} шт. в 1 лоте\n"
            f"Прямая ссылка TV:     {s['tv_url']}\n"
        )
        self.detail_text.insert(tk.END, info)

        try:
            dep = float(self.deposit_var.get())
            risk_pct = float(self.risk_pct_var.get())
        except ValueError:
            dep = 150000.0
            risk_pct = 1.0

        max_risk_rub = dep * (risk_pct / 100.0)
        stop_dist = abs(s["entry"] - s["sl"])
        risk_per_lot = stop_dist * s["lot"]
        lots = int(max(1, math.floor(max_risk_rub / risk_per_lot))) if risk_per_lot > 0 else 1
        shares = lots * s["lot"]
        pos_val = shares * s["entry"]
        exp_profit = lots * abs(s["tp"] - s["entry"]) * s["lot"]

        self.calc_result_lbl.configure(
            text=(
                f"Рекомендуемый объем: {lots} лотов ({shares} акций)\n"
                f"Сумма позиции:       {pos_val:,.0f} ₽\n"
                f"Риск на Стоп-лосс:  -{lots * risk_per_lot:,.0f} ₽ (лимит {max_risk_rub:,.0f} ₽)\n"
                f"Профит на Тейк:     +{exp_profit:,.0f} ₽"
            )
        )


if __name__ == "__main__":
    root = tk.Tk()
    app = MoexPulseDesktopApp(root)
    root.mainloop()
