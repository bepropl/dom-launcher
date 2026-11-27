package pl.dom.launcher.config

/**
 * Strona otwierana na telefonie po zeskanowaniu kodu QR.
 * Menu na dole i pięć sekcji: Przegląd, Dom (Home Assistant), Oglądanie, Połączenia, Więcej.
 * Każda zmiana zapisuje się od razu.
 * Uwaga: to surowy tekst Kotlina – w JavaScripcie poniżej nie używamy znaku dolara.
 */
internal const val CONFIG_PAGE = """<!doctype html>
<html lang="pl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#0B0806">
<title>Dom – ustawienia</title>
<style>
  :root { color-scheme: dark; --bg: #0B0806; --card: #17110C; --card2: #201811; --line: rgba(242,166,90,.18); --amber: #F2A65A; --text: #F4EBDD; --soft: #D9C9B2; --muted: #9C8A73; --ok: #7BC47F; --bad: #E8735A; }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--bg); color: var(--text); font: 16px/1.45 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; -webkit-tap-highlight-color: transparent; }
  header { position: sticky; top: 0; z-index: 5; background: rgba(11,8,6,.92); backdrop-filter: blur(8px); padding: 14px 18px 10px; border-bottom: 1px solid var(--line); }
  header h1 { margin: 0; font-size: 20px; font-weight: 600; }
  header p { margin: 2px 0 0; color: var(--muted); font-size: 13px; }
  main { max-width: 640px; margin: 0 auto; padding: 14px 14px 110px; }
  .view { display: none; }
  .view.on { display: block; }
  .card { background: var(--card); border: 1px solid var(--line); border-radius: 16px; padding: 16px; margin-bottom: 14px; }
  .card h2 { margin: 0 0 4px; font-size: 17px; font-weight: 600; }
  .card h3 { margin: 16px 0 6px; font-size: 14px; font-weight: 600; color: var(--amber); text-transform: uppercase; letter-spacing: .04em; }
  .hint { color: var(--muted); font-size: 13px; margin: 2px 0 10px; }
  label { display: block; font-size: 13px; color: var(--soft); margin: 12px 0 4px; }
  input, select { width: 100%; padding: 13px 14px; border-radius: 12px; border: 1px solid rgba(255,255,255,.12); background: var(--card2); color: var(--text); font-size: 16px; outline: none; }
  input:focus, select:focus { border-color: var(--amber); }
  button { font: inherit; cursor: pointer; border-radius: 12px; border: 1px solid rgba(242,166,90,.45); background: transparent; color: var(--text); padding: 11px 16px; min-height: 44px; }
  button.primary { background: var(--amber); color: #1A1008; border-color: var(--amber); font-weight: 600; }
  button.small { min-height: 34px; padding: 6px 12px; font-size: 14px; }
  button.link { border: none; color: var(--amber); padding: 6px 0; min-height: 0; }
  button:disabled { opacity: .5; }
  .row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
  .row > input { flex: 1; min-width: 0; }
  .status { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid rgba(255,255,255,.06); }
  .status:last-child { border-bottom: none; }
  .dot { width: 12px; height: 12px; border-radius: 50%; flex: none; background: var(--muted); }
  .dot.ok { background: var(--ok); box-shadow: 0 0 8px rgba(123,196,127,.6); }
  .dot.bad { background: var(--bad); }
  .status .grow { flex: 1; min-width: 0; }
  .status b { display: block; font-weight: 600; }
  .status span { color: var(--muted); font-size: 13px; }
  .field { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border-radius: 12px; background: var(--card2); margin-top: 6px; }
  .field .grow { flex: 1; min-width: 0; }
  .field .val { font-size: 15px; }
  .field .sub { color: var(--muted); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .chips { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 6px; }
  .chip { display: inline-flex; align-items: center; gap: 6px; padding: 8px 12px; border-radius: 999px; border: 1px solid rgba(255,255,255,.14); background: var(--card2); font-size: 14px; user-select: none; }
  .chip.on { border-color: var(--amber); background: rgba(242,166,90,.16); color: var(--text); }
  .chip .x { color: var(--muted); font-size: 16px; margin-left: 2px; }
  .picker { margin-top: 8px; border: 1px solid var(--line); border-radius: 12px; overflow: hidden; background: var(--card2); }
  .picker input { border: none; border-bottom: 1px solid var(--line); border-radius: 0; }
  .picker ul { list-style: none; margin: 0; padding: 0; max-height: 300px; overflow-y: auto; }
  .picker li { padding: 11px 14px; border-bottom: 1px solid rgba(255,255,255,.05); }
  .picker li small { display: block; color: var(--muted); font-size: 12px; }
  .picker li.none { color: var(--muted); }
  .room { padding: 12px 0; border-bottom: 1px solid rgba(255,255,255,.06); }
  .room:last-child { border-bottom: none; }
  .room .name { font-weight: 600; }
  .media { list-style: none; margin: 8px 0 0; padding: 0; }
  .media li { display: flex; gap: 12px; padding: 12px 0; border-bottom: 1px solid rgba(255,255,255,.06); }
  .media li:last-child { border-bottom: none; }
  .media img, .media .noimg { width: 54px; height: 81px; object-fit: cover; border-radius: 8px; background: var(--card2); flex: none; }
  .media .info { flex: 1; min-width: 0; }
  .media .title { font-weight: 600; }
  .media .meta { color: var(--muted); font-size: 13px; }
  .media .acts { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; align-items: center; }
  .media .acts input { width: 64px; padding: 8px; font-size: 15px; }
  .tag { color: var(--ok); font-size: 13px; }
  details { margin-top: 10px; }
  summary { color: var(--amber); cursor: pointer; font-size: 14px; }
  pre { background: #0F0B08; border-radius: 10px; padding: 12px; font-size: 12px; overflow-x: auto; color: var(--soft); }
  code.fp { display: block; word-break: break-all; font-size: 12px; background: #0F0B08; padding: 10px; border-radius: 10px; margin: 8px 0; }
  nav { position: fixed; left: 0; right: 0; bottom: 0; z-index: 10; display: flex; background: rgba(17,12,9,.97); border-top: 1px solid var(--line); padding-bottom: env(safe-area-inset-bottom); }
  nav button { flex: 1; border: none; border-radius: 0; min-height: 62px; padding: 8px 2px; color: var(--muted); display: flex; flex-direction: column; align-items: center; gap: 2px; font-size: 11px; }
  nav button .ic { font-size: 20px; line-height: 1; }
  nav button.on { color: var(--amber); }
  #toast { position: fixed; left: 50%; bottom: 80px; transform: translateX(-50%) translateY(20px); opacity: 0; transition: .25s; background: #2A2017; border: 1px solid var(--line); color: var(--text); padding: 10px 18px; border-radius: 999px; font-size: 14px; z-index: 20; pointer-events: none; max-width: 90vw; text-align: center; }
  #toast.show { opacity: 1; transform: translateX(-50%) translateY(0); }
  #login { max-width: 420px; margin: 12vh auto 0; padding: 0 18px; }
  #login .card { text-align: center; }
  #pin { text-align: center; font-size: 28px; letter-spacing: .3em; }
  .hidden { display: none !important; }
  .empty { color: var(--muted); font-size: 14px; padding: 8px 0; }
</style>
</head>
<body>

<div id="login">
  <div class="card">
    <h2>Dom – ustawienia</h2>
    <p class="hint">Wpisz 6-cyfrowy PIN widoczny na telewizorze.</p>
    <input id="pin" inputmode="numeric" maxlength="6" autocomplete="one-time-code" aria-label="PIN">
    <p style="margin-top:14px"><button class="primary" id="loginBtn" type="button" style="width:100%">Połącz</button></p>
    <p class="hint" id="loginMsg"></p>
  </div>
</div>

<div id="app" class="hidden">
  <header><h1 id="title">Przegląd</h1><p id="subtitle">Dom – launcher na telewizor</p></header>
  <main>

    <!-- PRZEGLĄD -->
    <section class="view on" id="v-overview">
      <div class="card">
        <h2>Stan</h2>
        <div id="statusList"><p class="empty">Sprawdzam…</p></div>
      </div>
      <div class="card">
        <h2>W skrócie</h2>
        <div id="counts" class="hint" style="margin:0"></div>
      </div>
    </section>

    <!-- DOM -->
    <section class="view" id="v-home">
      <div class="card" id="haOff">
        <h2>Home Assistant nie jest połączony</h2>
        <p class="hint" id="haOffMsg"></p>
        <button class="small" type="button" data-go="links">Przejdź do Połączeń</button>
      </div>
      <div class="card hidden" id="haTrust">
        <h2>Zaufaj certyfikatowi</h2>
        <p class="hint">Home Assistant ma własny certyfikat. Jeśli to Twój serwer, zaufaj mu – launcher będzie akceptował tylko ten jeden.</p>
        <code class="fp" id="haFingerprint"></code>
        <button class="primary" type="button" id="haTrustBtn">Ufaj temu certyfikatowi</button>
      </div>
      <div id="haOn" class="hidden">
        <div class="card">
          <h2>Pogoda i temperatury</h2>
          <p class="hint">Pokazywane w pasku u góry i na wygaszaczu.</p>
          <div id="fWeather"></div>
          <div id="fOutside"></div>
          <div id="fInside"></div>
        </div>
        <div class="card">
          <h2>Osoby w domu</h2>
          <p class="hint">Zaznacz, kogo liczyć. Dotknij, żeby włączyć lub wyłączyć.</p>
          <div id="persons" class="chips"></div>
        </div>
        <div class="card">
          <h2>Światła na domku</h2>
          <p class="hint">Pokój na domku świeci, gdy włączone jest którekolwiek z jego świateł.</p>
          <div id="rooms"></div>
        </div>
        <div class="card">
          <h2>Powiadomienia</h2>
          <p class="hint">Dymki na telewizorze z automatyzacji Home Assistanta.</p>
          <div id="fCamera"></div>
          <p style="margin-top:12px"><button class="primary" type="button" id="testBtn">Wyślij testowe powiadomienie</button></p>
          <details>
            <summary>Jak wysłać powiadomienie z automatyzacji</summary>
            <p class="hint">W automatyzacji dodaj akcję „Wywołaj zdarzenie” (edycja w YAML):</p>
            <pre>- event: dom_launcher_notify
  event_data:
    title: Pralka
    message: Pranie skończone
    camera: camera.furtka   # opcjonalnie
    seconds: 15             # opcjonalnie</pre>
            <p class="hint">Nad innymi aplikacjami dymek pokaże się, gdy na telewizorze włączona jest usługa „Dom – ekran główny”.</p>
          </details>
        </div>
      </div>
    </section>

    <!-- OGLĄDANIE -->
    <section class="view" id="v-watch">
      <div class="card">
        <h2>Szukaj</h2>
        <input id="q" type="search" placeholder="Tytuł serialu lub filmu" autocomplete="off" aria-label="Szukaj">
        <ul class="media" id="results"></ul>
      </div>
      <div class="card">
        <h2>Obserwowane seriale</h2>
        <p class="hint">„Obejrzane do” – ostatni odcinek, który widzieliście (S0 E0 = jeszcze nie zaczęte).</p>
        <ul class="media" id="shows"></ul>
      </div>
      <div class="card">
        <h2>Do obejrzenia</h2>
        <ul class="media" id="watchlist"></ul>
      </div>
      <div class="card">
        <h2>Wasze serwisy</h2>
        <p class="hint" id="provMsg">Dotknij serwisu, żeby go włączyć lub wyłączyć.</p>
        <div id="providers" class="chips"></div>
      </div>
    </section>

    <!-- POŁĄCZENIA -->
    <section class="view" id="v-links">
      <div class="card">
        <h2>Home Assistant</h2>
        <label for="haUrl">Adres</label>
        <input id="haUrl" placeholder="https://192.168.100.131:8123" autocomplete="off" inputmode="url">
        <label for="ha_token">Token długoterminowy <span class="tag" id="has_ha_token"></span></label>
        <input id="ha_token" type="password" autocomplete="off" placeholder="Wklej token (puste = bez zmian)">
        <p class="hint">HA → Twój profil → Bezpieczeństwo → Tokeny dostępu długoterminowego → Utwórz token.</p>
        <div class="row"><button class="small" type="button" data-check="ha">Sprawdź połączenie</button><span class="hint" id="chk-ha" style="margin:0"></span></div>
      </div>
      <div class="card">
        <h2>Emby (NAS)</h2>
        <label for="embyUrl">Adres</label>
        <input id="embyUrl" placeholder="http://192.168.1.20:8096" autocomplete="off" inputmode="url">
        <label for="emby_key">Klucz API <span class="tag" id="has_emby_key"></span></label>
        <input id="emby_key" type="password" autocomplete="off" placeholder="Wklej klucz (puste = bez zmian)">
        <p class="hint">Emby → Panel sterowania → Klucze API → Nowy klucz.</p>
        <div class="row"><button class="small" type="button" data-check="emby">Sprawdź połączenie</button><span class="hint" id="chk-emby" style="margin:0"></span></div>
      </div>
      <div class="card">
        <h2>TMDB (seriale i filmy)</h2>
        <label for="tmdb_key">Klucz API lub token odczytu <span class="tag" id="has_tmdb_key"></span></label>
        <input id="tmdb_key" type="password" autocomplete="off" placeholder="Wklej klucz (puste = bez zmian)">
        <p class="hint">Darmowy: themoviedb.org → Ustawienia → API.</p>
        <div class="row"><button class="small" type="button" data-check="tmdb">Sprawdź połączenie</button><span class="hint" id="chk-tmdb" style="margin:0"></span></div>
      </div>
      <p class="hint" style="text-align:center">Klucz usuniesz, wpisując „-” i zatwierdzając.</p>
    </section>

    <!-- WIĘCEJ -->
    <section class="view" id="v-more">
      <div class="card">
        <h2>Ulubione aplikacje</h2>
        <p class="hint">Rząd na dole ekranu Start. Kolejność zmienisz też na telewizorze – przytrzymaj OK na aplikacji.</p>
        <ul class="media" id="favs"></ul>
        <div id="fAddApp"></div>
      </div>
      <div class="card">
        <h2>Kopia zapasowa</h2>
        <p class="hint">Obserwowane seriale i lista do obejrzenia w jednym pliku.</p>
        <div class="row">
          <button type="button" id="exportBtn">Pobierz kopię</button>
          <button type="button" id="importBtn">Wczytaj kopię</button>
          <input id="importFile" type="file" accept="application/json,.json" class="hidden">
        </div>
      </div>
      <div class="card">
        <h2>O launcherze</h2>
        <p class="hint" id="about" style="margin:0"></p>
      </div>
    </section>

  </main>

  <nav>
    <button type="button" class="on" data-view="overview"><span class="ic">◉</span>Przegląd</button>
    <button type="button" data-view="home"><span class="ic">⌂</span>Dom</button>
    <button type="button" data-view="watch"><span class="ic">▶</span>Oglądanie</button>
    <button type="button" data-view="links"><span class="ic">⚿</span>Połączenia</button>
    <button type="button" data-view="more"><span class="ic">⋯</span>Więcej</button>
  </nav>
</div>

<div id="toast"></div>

<script>
(function () {
  var pin = "";
  var state = null, ha = null;
  var TITLES = { overview: "Przegląd", home: "Dom", watch: "Oglądanie", links: "Połączenia", more: "Więcej" };
  var SUBS = { overview: "Stan launchera i połączeń", home: "Home Assistant: pogoda, osoby, światła, powiadomienia", watch: "Seriale, filmy i Wasze serwisy", links: "Adresy i klucze", more: "Ulubione aplikacje, kopia zapasowa" };

  function el(id) { return document.getElementById(id); }
  function h(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text !== undefined && text !== null) n.textContent = text;
    return n;
  }
  function btn(text, cls, onClick) {
    var b = h("button", cls || "small", text);
    b.type = "button";
    b.onclick = function (e) {
      e.stopPropagation();
      b.disabled = true;
      Promise.resolve(onClick()).then(function () { b.disabled = false; }, function () { b.disabled = false; });
    };
    return b;
  }
  var toastTimer = null;
  function toast(text) {
    var t = el("toast");
    t.textContent = text;
    t.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.classList.remove("show"); }, 2200);
  }
  function api(path, body) {
    var options = { headers: { "X-Pin": pin } };
    if (body !== undefined) {
      options.method = "POST";
      options.headers["Content-Type"] = "application/json; charset=utf-8";
      options.body = JSON.stringify(body);
    }
    return fetch(path, options).then(function (r) {
      return r.json().then(function (data) {
        if (!r.ok) throw new Error(data.error || ("Błąd " + r.status));
        return data;
      });
    });
  }
  function fail(e) { toast(e.message || "Coś poszło nie tak"); }
  function norm(t) {
    return (t || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/ł/g, "l");
  }

  // ---------- nawigacja ----------
  var current = "overview";
  function show(view) {
    current = view;
    document.querySelectorAll(".view").forEach(function (v) { v.classList.toggle("on", v.id === "v-" + view); });
    document.querySelectorAll("nav button").forEach(function (b) { b.classList.toggle("on", b.dataset.view === view); });
    el("title").textContent = TITLES[view];
    el("subtitle").textContent = SUBS[view];
    window.scrollTo(0, 0);
    if (view === "overview") loadStatus();
    if (view === "home") loadHa();
    if (view === "watch") { loadLibrary(); loadProviders(); }
  }
  document.querySelectorAll("nav button").forEach(function (b) { b.onclick = function () { show(b.dataset.view); }; });
  document.addEventListener("click", function (e) {
    var go = e.target.closest && e.target.closest("[data-go]");
    if (go) show(go.dataset.go);
  });

  // ---------- wybieranie z listy (z wyszukiwaniem) ----------
  // items: [{id, name, sub}], onPick(item|null)
  function picker(container, items, onPick, allowNone, noneLabel) {
    container.querySelectorAll(".picker").forEach(function (p) { p.remove(); });
    var box = h("div", "picker");
    var input = h("input");
    input.type = "search";
    input.placeholder = "Szukaj (np. salon)…";
    var list = h("ul");
    box.appendChild(input);
    box.appendChild(list);
    function render() {
      var q = norm(input.value);
      list.textContent = "";
      if (allowNone) {
        var none = h("li", "none", noneLabel || "— brak —");
        none.onclick = function () { box.remove(); onPick(null); };
        list.appendChild(none);
      }
      var shown = 0;
      items.forEach(function (it) {
        if (q && norm(it.name).indexOf(q) < 0 && norm(it.id).indexOf(q) < 0) return;
        if (shown >= 80) return;
        shown++;
        var li = h("li", "", it.name);
        li.appendChild(h("small", "", it.sub || it.id));
        li.onclick = function () { box.remove(); onPick(it); };
        list.appendChild(li);
      });
      if (!shown) list.appendChild(h("li", "none", "Nic nie pasuje."));
    }
    input.oninput = render;
    render();
    container.appendChild(box);
    input.focus();
  }
  function entityItems(list) { return list.map(function (e) { return { id: e.id, name: e.name, sub: e.id + " · " + e.state }; }); }
  function nameOf(list, id) {
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return list[i].name;
    return id;
  }

  // Pole z jedną wybraną encją: „Pogoda: Dom   [Zmień]”
  function singleField(container, label, list, value, emptyText, onChange) {
    container.textContent = "";
    container.appendChild(h("label", "", label));
    var row = h("div", "field");
    var g = h("div", "grow");
    g.appendChild(h("div", "val", value ? nameOf(list, value) : emptyText));
    if (value) g.appendChild(h("div", "sub", value));
    row.appendChild(g);
    row.appendChild(btn(value ? "Zmień" : "Wybierz", "small", function () {
      picker(container, entityItems(list), function (it) { onChange(it ? it.id : null); }, true, emptyText);
    }));
    container.appendChild(row);
  }

  // ---------- PRZEGLĄD ----------
  function statusRow(ok, title, text, go) {
    var r = h("div", "status");
    r.appendChild(h("div", "dot " + (ok === null ? "" : ok ? "ok" : "bad")));
    var g = h("div", "grow");
    g.appendChild(h("b", "", title));
    g.appendChild(h("span", "", text));
    r.appendChild(g);
    if (ok === false && go) {
      var b = btn("Napraw", "small", function () { show(go); });
      r.appendChild(b);
    }
    return r;
  }
  function loadStatus() {
    api("/api/status").then(function (s) {
      var list = el("statusList");
      list.textContent = "";
      var haOk = s.ha.status === "connected" ? true : (s.ha.status === "connecting" ? null : false);
      list.appendChild(statusRow(haOk, "Home Assistant", s.ha.message, s.ha.status === "untrusted" ? "home" : "links"));
      list.appendChild(statusRow(s.tmdb.ok, "TMDB (seriale i filmy)", s.tmdb.message, "links"));
      list.appendChild(statusRow(s.emby.ok, "Emby (NAS)", s.emby.message, "links"));
      list.appendChild(statusRow(s.providers > 0, "Serwisy streamingowe", s.providers > 0 ? ("Zaznaczone: " + s.providers) : "Zaznacz swoje serwisy", "watch"));
      if (s.update) list.appendChild(statusRow(null, "Aktualizacja", s.update));
      el("counts").textContent = "Obserwowane seriale: " + s.shows + " · Do obejrzenia: " + s.watchlist + " · Wersja " + s.version;
      el("about").textContent = "Dom " + s.version + ". Aktualizacje instalujesz na telewizorze: zębatka → Aktualizacja Dom.";
    }).catch(fail);
  }

  // ---------- DOM (Home Assistant) ----------
  var haPolls = 0;
  function saveHa(part, msg) {
    return api("/api/ha/config", part).then(function () { toast(msg || "Zapisano ✓"); return loadHa(true); }).catch(fail);
  }
  function loadHa(quiet) {
    return api("/api/ha").then(function (d) {
      ha = d;
      var st = d.status;
      var connected = st.status === "connected";
      el("haOff").classList.toggle("hidden", connected || st.status === "untrusted");
      el("haTrust").classList.toggle("hidden", st.status !== "untrusted");
      el("haOn").classList.toggle("hidden", !connected);
      el("haOffMsg").textContent = st.message || "";
      if (st.status === "untrusted") el("haFingerprint").textContent = st.fingerprint;
      if (st.status === "connecting" && haPolls < 15) { haPolls++; setTimeout(loadHa, 2000); }
      if (!connected) return;
      var c = d.config;
      singleField(el("fWeather"), "Pogoda", d.weather, c.weather, "— brak —", function (v) { saveHa({ weather: v || "" }); });
      singleField(el("fOutside"), "Temperatura na zewnątrz", d.temps, c.outsideTemp, "— z pogody —", function (v) { saveHa({ outsideTemp: v || "" }); });
      singleField(el("fInside"), "Temperatura w domu", d.temps, c.insideTemp, "— brak —", function (v) { saveHa({ insideTemp: v || "" }); });
      singleField(el("fCamera"), "Kamera do testu (opcjonalnie)", d.cameras, el("fCamera").dataset.value || null, "— bez obrazu —", function (v) {
        el("fCamera").dataset.value = v || "";
        loadHa(true);
      });

      var persons = el("persons");
      persons.textContent = "";
      if (!d.persons.length) persons.appendChild(h("span", "empty", "Brak encji person.* w Home Assistancie."));
      d.persons.forEach(function (p) {
        var on = c.persons.indexOf(p.id) >= 0;
        var chip = h("span", "chip" + (on ? " on" : ""), (on ? "✓ " : "") + p.name);
        chip.onclick = function () {
          var next = c.persons.filter(function (x) { return x !== p.id; });
          if (!on) next.push(p.id);
          saveHa({ persons: next });
        };
        persons.appendChild(chip);
      });

      var rooms = el("rooms");
      rooms.textContent = "";
      d.rooms.forEach(function (r) {
        var key = String(r.id);
        var chosen = (c.rooms[key] || []).slice();
        var box = h("div", "room");
        box.appendChild(h("div", "name", r.name));
        var chips = h("div", "chips");
        if (!chosen.length) chips.appendChild(h("span", "empty", "Brak świateł"));
        chosen.forEach(function (id) {
          var chip = h("span", "chip on", nameOf(d.lights, id));
          var x = h("span", "x", "✕");
          chip.appendChild(x);
          chip.onclick = function () {
            var all = {}; Object.keys(c.rooms).forEach(function (k) { all[k] = c.rooms[k]; });
            all[key] = chosen.filter(function (e) { return e !== id; });
            saveHa({ rooms: all }, "Usunięto światło");
          };
          chips.appendChild(chip);
        });
        box.appendChild(chips);
        var add = btn("+ Dodaj światło", "link", function () {
          var free = d.lights.filter(function (l) { return chosen.indexOf(l.id) < 0; });
          picker(box, entityItems(free), function (it) {
            if (!it) return;
            var all = {}; Object.keys(c.rooms).forEach(function (k) { all[k] = c.rooms[k]; });
            all[key] = chosen.concat([it.id]);
            saveHa({ rooms: all }, "Dodano: " + it.name);
          }, false);
        });
        box.appendChild(add);
        rooms.appendChild(box);
      });
    }).catch(function (e) { if (!quiet) fail(e); });
  }
  el("haTrustBtn").onclick = function () {
    api("/api/ha/trust", { fingerprint: el("haFingerprint").textContent })
      .then(function () { toast("Zapisano – łączę…"); haPolls = 0; setTimeout(loadHa, 1500); })
      .catch(fail);
  };
  el("testBtn").onclick = function () {
    api("/api/ha/test", { camera: el("fCamera").dataset.value || "" })
      .then(function () { toast("Wysłano – spójrz na telewizor"); })
      .catch(fail);
  };

  // ---------- OGLĄDANIE ----------
  function mediaRow(poster, title, meta) {
    var li = h("li");
    if (poster) { var img = h("img"); img.src = poster; img.alt = ""; img.loading = "lazy"; li.appendChild(img); }
    else li.appendChild(h("div", "noimg"));
    var info = h("div", "info");
    info.appendChild(h("div", "title", title));
    if (meta) info.appendChild(h("div", "meta", meta));
    var acts = h("div", "acts");
    info.appendChild(acts);
    li.appendChild(info);
    return { li: li, acts: acts };
  }
  function libAction(payload, okText) {
    return api("/api/library", payload).then(function (lib) {
      renderLibrary(lib);
      toast(okText || "Zapisano ✓");
      if (lastQuery) search(lastQuery);
    }).catch(fail);
  }
  function showMeta(s) {
    var parts = [];
    if (s.provider) parts.push(s.provider);
    if (s.unwatched > 0 && s.next) parts.push("dalej " + s.next.code + " (" + s.unwatched + " czeka)");
    else if (s.nextAir) parts.push("następny " + s.nextAir.code + (s.nextAir.date ? " – " + s.nextAir.date : ""));
    else if (s.ended) parts.push("zakończony");
    else parts.push("na bieżąco");
    return parts.join(" · ");
  }
  function renderLibrary(lib) {
    var shows = el("shows");
    shows.textContent = "";
    if (!lib.shows.length) shows.appendChild(h("li", "empty", "Jeszcze nic. Wyszukaj serial powyżej."));
    lib.shows.forEach(function (s) {
      var r = mediaRow(s.poster, s.name, showMeta(s));
      r.acts.appendChild(h("span", "meta", "Obejrzane do S"));
      var se = h("input"); se.type = "number"; se.min = "0"; se.inputMode = "numeric"; se.value = s.watched.s;
      r.acts.appendChild(se);
      r.acts.appendChild(h("span", "meta", "E"));
      var ep = h("input"); ep.type = "number"; ep.min = "0"; ep.inputMode = "numeric"; ep.value = s.watched.e;
      r.acts.appendChild(ep);
      function saveEp() { libAction({ action: "watched", id: s.id, s: parseInt(se.value || "0", 10), e: parseInt(ep.value || "0", 10) }); }
      se.onchange = saveEp; ep.onchange = saveEp;
      if (s.unwatched > 0 && s.next) r.acts.appendChild(btn("Obejrzany " + s.next.code, "small", function () { return libAction({ action: "next", id: s.id }); }));
      r.acts.appendChild(btn("Usuń", "small", function () {
        if (!confirm("Przestać obserwować „" + s.name + "”?")) return;
        return libAction({ action: "unfollow", id: s.id }, "Usunięto");
      }));
      shows.appendChild(r.li);
    });
    var wl = el("watchlist");
    wl.textContent = "";
    if (!lib.watchlist.length) wl.appendChild(h("li", "empty", "Pusta. Dodasz tu filmy i seriale z wyszukiwarki."));
    lib.watchlist.forEach(function (w) {
      var r = mediaRow(w.poster, w.title, (w.type === "MOVIE" ? "Film" : "Serial") + (w.provider ? " · " + w.provider : ""));
      r.acts.appendChild(btn("Usuń z listy", "small", function () { return libAction({ action: "unlist", id: w.id, type: w.type }, "Usunięto"); }));
      wl.appendChild(r.li);
    });
  }
  function loadLibrary() { api("/api/library").then(renderLibrary).catch(fail); }

  var lastQuery = "", searchTimer = null;
  function search(q) {
    lastQuery = q;
    var list = el("results");
    if (!q) { list.textContent = ""; return; }
    api("/api/search", { q: q }).then(function (res) {
      if (q !== lastQuery) return;
      list.textContent = "";
      if (res.error) { list.appendChild(h("li", "empty", res.error)); return; }
      if (!res.results.length) { list.appendChild(h("li", "empty", "Nic nie znalazłem.")); return; }
      res.results.forEach(function (it) {
        var r = mediaRow(it.poster, it.title, (it.type === "MOVIE" ? "Film" : "Serial") + (it.year ? " · " + it.year : ""));
        if (it.type === "TV") {
          if (it.followed) r.acts.appendChild(h("span", "tag", "Obserwujecie ✓"));
          else {
            r.acts.appendChild(btn("Obserwuj – na bieżąco", "small", function () { return libAction({ action: "follow", id: it.id, caughtUp: true }, "Obserwujecie ✓"); }));
            r.acts.appendChild(btn("Obserwuj – od początku", "small", function () { return libAction({ action: "follow", id: it.id, caughtUp: false }, "Obserwujecie ✓"); }));
          }
        }
        if (it.onList) r.acts.appendChild(h("span", "tag", "Na liście ✓"));
        else if (!it.followed) r.acts.appendChild(btn("+ Do obejrzenia", "small", function () { return libAction({ action: "list", id: it.id, type: it.type }, "Dodano ✓"); }));
        list.appendChild(r.li);
      });
    }).catch(fail);
  }
  el("q").addEventListener("input", function () {
    clearTimeout(searchTimer);
    var q = el("q").value.trim();
    searchTimer = setTimeout(function () { search(q); }, 600);
  });

  function loadProviders() {
    api("/api/providers").then(function (p) {
      var box = el("providers");
      box.textContent = "";
      if (p.error) el("provMsg").textContent = p.error;
      var selected = p.selected.slice();
      p.all.forEach(function (prov) {
        var on = selected.indexOf(prov.id) >= 0;
        var chip = h("span", "chip" + (on ? " on" : ""), (on ? "✓ " : "") + prov.name);
        chip.onclick = function () {
          var next = p.all.filter(function (x) {
            var was = selected.indexOf(x.id) >= 0;
            return x.id === prov.id ? !was : was;
          }).map(function (x) { return { id: x.id, name: x.name }; });
          api("/api/providers", { providers: next }).then(function () { toast("Zapisano ✓"); loadProviders(); }).catch(fail);
        };
        box.appendChild(chip);
      });
    }).catch(fail);
  }

  // ---------- POŁĄCZENIA ----------
  function saveField(body, okText) { return api("/api/save", body).then(function () { toast(okText || "Zapisano ✓"); return loadState(); }).catch(fail); }
  el("haUrl").onchange = function () { saveField({ haUrl: el("haUrl").value.trim() }, "Zapisano adres – łączę…"); haPolls = 0; };
  el("embyUrl").onchange = function () { saveField({ embyUrl: el("embyUrl").value.trim() }); };
  ["ha_token", "emby_key", "tmdb_key"].forEach(function (name) {
    el(name).onchange = function () {
      var v = el(name).value.trim();
      if (!v) return;
      var secrets = {}; secrets[name] = v;
      saveField({ secrets: secrets }, v === "-" ? "Usunięto klucz" : "Zapisano klucz ✓").then(function () { el(name).value = ""; });
    };
  });
  document.querySelectorAll("[data-check]").forEach(function (b) {
    b.onclick = function () {
      var what = b.dataset.check;
      var out = el("chk-" + what);
      out.textContent = "Sprawdzam…";
      api("/api/status").then(function (s) {
        if (what === "ha") out.textContent = s.ha.message;
        else out.textContent = (s[what].ok ? "✓ " : "✕ ") + s[what].message;
      }).catch(function (e) { out.textContent = e.message; });
    };
  });

  // ---------- WIĘCEJ ----------
  function saveFavorites(list, msg) { return saveField({ favorites: list }, msg); }
  function renderFavorites() {
    var favs = el("favs");
    favs.textContent = "";
    var list = state.favorites.slice();
    function label(pkg) { for (var i = 0; i < state.apps.length; i++) if (state.apps[i].pkg === pkg) return state.apps[i].label; return pkg; }
    if (!list.length) favs.appendChild(h("li", "empty", "Brak – na Starcie pokazują się domyślne aplikacje."));
    list.forEach(function (pkg, i) {
      var li = h("li");
      var info = h("div", "info");
      info.appendChild(h("div", "title", (i + 1) + ". " + label(pkg)));
      var acts = h("div", "acts");
      acts.appendChild(btn("↑", "small", function () { if (i > 0) { list.splice(i - 1, 0, list.splice(i, 1)[0]); return saveFavorites(list); } }));
      acts.appendChild(btn("↓", "small", function () { if (i < list.length - 1) { list.splice(i + 1, 0, list.splice(i, 1)[0]); return saveFavorites(list); } }));
      acts.appendChild(btn("Usuń", "small", function () { list.splice(i, 1); return saveFavorites(list, "Usunięto"); }));
      info.appendChild(acts);
      li.appendChild(info);
      favs.appendChild(li);
    });
    var addBox = el("fAddApp");
    addBox.textContent = "";
    addBox.appendChild(btn("+ Dodaj aplikację", "link", function () {
      var free = state.apps.filter(function (a) { return list.indexOf(a.pkg) < 0; }).map(function (a) { return { id: a.pkg, name: a.label, sub: a.pkg }; });
      picker(addBox, free, function (it) { if (it) saveFavorites(list.concat([it.id]), "Dodano: " + it.name); }, false);
    }));
  }
  el("exportBtn").onclick = function () {
    fetch("/api/export", { headers: { "X-Pin": pin } }).then(function (r) {
      if (!r.ok) throw new Error("Błąd " + r.status);
      return r.blob();
    }).then(function (blob) {
      var a = h("a");
      a.href = URL.createObjectURL(blob);
      a.download = "dom-biblioteka-" + new Date().toISOString().slice(0, 10) + ".json";
      document.body.appendChild(a); a.click(); a.remove();
      toast("Pobrano ✓");
    }).catch(fail);
  };
  el("importBtn").onclick = function () { el("importFile").click(); };
  el("importFile").onchange = function () {
    var file = el("importFile").files[0];
    if (!file) return;
    if (!confirm("Wczytać kopię? Obecne seriale i lista zostaną zastąpione.")) { el("importFile").value = ""; return; }
    file.text().then(function (text) { return api("/api/import", { json: text }); })
      .then(function () { toast("Wczytano ✓"); })
      .catch(fail)
      .then(function () { el("importFile").value = ""; });
  };

  // ---------- start ----------
  function loadState() {
    return api("/api/state").then(function (s) {
      state = s;
      el("haUrl").value = s.haUrl || "";
      el("embyUrl").value = s.embyUrl || "";
      ["ha_token", "emby_key", "tmdb_key"].forEach(function (name) { el("has_" + name).textContent = s.secrets[name] ? "zapisany ✓" : ""; });
      renderFavorites();
    });
  }
  function start() {
    loadState().then(function () {
      el("login").classList.add("hidden");
      el("app").classList.remove("hidden");
      show("overview");
    }).catch(function (e) { el("loginMsg").textContent = e.message; });
  }
  el("loginBtn").onclick = function () { pin = el("pin").value.replace(/\D/g, ""); start(); };
  el("pin").addEventListener("keydown", function (e) { if (e.key === "Enter") el("loginBtn").onclick(); });
  var match = /pin=(\d{6})/.exec(location.hash);
  if (match) { pin = match[1]; el("pin").value = pin; history.replaceState(null, "", "/"); start(); }
})();
</script>
</body>
</html>
"""
