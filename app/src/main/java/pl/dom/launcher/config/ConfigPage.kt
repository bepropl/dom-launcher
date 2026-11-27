package pl.dom.launcher.config

/**
 * Strona otwierana na telefonie po zeskanowaniu kodu QR.
 * Uwaga: to surowy tekst Kotlina – w JavaScripcie poniżej nie używamy znaku dolara.
 */
internal const val CONFIG_PAGE = """<!doctype html>
<html lang="pl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Dom – ustawienia launchera</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; background: #070504; color: #F4EBDD; font: 16px/1.5 system-ui, -apple-system, Segoe UI, Roboto, sans-serif; }
  main { max-width: 640px; margin: 0 auto; padding: 20px 16px 120px; }
  h1 { font-size: 24px; font-weight: 500; margin: 8px 0 4px; }
  h2 { font-size: 18px; font-weight: 500; margin: 0 0 10px; }
  p.hint { color: #B9A68C; font-size: 14px; margin: 4px 0 0; }
  section { background: #14100C; border: 1px solid rgba(242,166,90,.2); border-radius: 14px; padding: 16px; margin-top: 16px; }
  label { display: block; font-size: 14px; color: #D9C7AE; margin: 12px 0 4px; }
  input, select { width: 100%; box-sizing: border-box; padding: 12px; border-radius: 10px; border: 1px solid rgba(255,255,255,.15); background: #0d0a08; color: #F4EBDD; font-size: 16px; }
  input:focus, select:focus { outline: 2px solid #F2A65A; outline-offset: 1px; }
  button { min-height: 44px; padding: 0 16px; border-radius: 10px; border: 1px solid rgba(242,166,90,.45); background: transparent; color: #F4EBDD; font-size: 15px; cursor: pointer; }
  button.primary { background: #F2A65A; color: #1A1009; border: none; font-weight: 600; }
  .row { display: flex; gap: 8px; align-items: center; }
  .row > input, .row > select { flex: 1; }
  ul { list-style: none; padding: 0; margin: 0; }
  li { display: flex; align-items: center; gap: 6px; padding: 6px 0; border-bottom: 1px solid rgba(255,255,255,.06); }
  li span { flex: 1; }
  li button { min-height: 40px; min-width: 44px; padding: 0 10px; }
  .saved { color: #5DCAA5; font-size: 13px; }
  .bar { position: fixed; left: 0; right: 0; bottom: 0; background: #0d0a08; border-top: 1px solid rgba(242,166,90,.25); padding: 12px 16px; }
  .bar .inner { max-width: 640px; margin: 0 auto; display: flex; gap: 12px; align-items: center; }
  #msg { flex: 1; font-size: 14px; color: #D9C7AE; }
  .hidden { display: none; }
  select { width: 100%; padding: 12px; border-radius: 10px; border: 1px solid rgba(255,255,255,.15); background: #241c15; color: #F4EBDD; font-size: 16px; margin-bottom: 10px; }
  h3 { font-size: 15px; font-weight: 500; margin: 18px 0 4px; color: #F2A65A; }
  pre { background: #120d0a; border-radius: 10px; padding: 10px; font-size: 12px; overflow-x: auto; color: #D9C9B2; }
  .fp { display: block; word-break: break-all; font-size: 12px; background: #120d0a; padding: 8px; border-radius: 8px; margin-bottom: 8px; }
  .room { display: flex; gap: 8px; align-items: center; }
  .room span { flex: 0 0 42%; font-size: 14px; }
  .room select { margin-bottom: 6px; }
  .checks { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
  .checks label { display: flex; align-items: center; gap: 8px; margin: 0; padding: 8px 12px; border-radius: 10px; border: 1px solid rgba(255,255,255,.15); color: #F4EBDD; font-size: 15px; }
  .checks input { width: auto; margin: 0; }
  ul.media li { align-items: flex-start; gap: 10px; padding: 10px 0; flex-wrap: wrap; }
  ul.media img, ul.media .noimg { width: 46px; height: 69px; object-fit: cover; border-radius: 6px; background: #241c15; flex: none; }
  ul.media .info { flex: 1; min-width: 160px; }
  ul.media .title { font-weight: 500; }
  ul.media .meta { color: #B9A68C; font-size: 13px; }
  ul.media .acts { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
  ul.media .acts input { width: 64px; padding: 8px; }
  ul.media button { min-height: 40px; font-size: 14px; }
  .row.wrap { flex-wrap: wrap; }
  .filebtn { display: inline-flex; align-items: center; min-height: 44px; padding: 0 16px; margin: 0; border-radius: 10px; border: 1px solid rgba(242,166,90,.45); color: #F4EBDD; font-size: 15px; cursor: pointer; }
  .filebtn input { display: none; }
</style>
</head>
<body>
<main>
  <h1>Dom – ustawienia launchera</h1>
  <p class="hint">Zmiany zapisują się na telewizorze. Klucze są tam szyfrowane i nie są tu ponownie wyświetlane.</p>

  <section id="login">
    <h2>PIN z ekranu telewizora</h2>
    <div class="row">
      <input id="pin" inputmode="numeric" autocomplete="one-time-code" placeholder="123456" aria-label="PIN">
      <button class="primary" id="loginBtn" type="button">Połącz</button>
    </div>
    <p class="hint" id="loginMsg"></p>
  </section>

  <div id="form" class="hidden">
    <section>
      <h2>Aplikacje na ekranie Start</h2>
      <ul id="favList"></ul>
      <label for="addApp">Dodaj aplikację</label>
      <div class="row">
        <select id="addApp"></select>
        <button type="button" id="addBtn">Dodaj</button>
      </div>
    </section>

    <section>
      <h2>Ochrona ekranu OLED</h2>
      <label for="idle">Tryb spoczynku po (minutach, 0 = nigdy)</label>
      <input id="idle" type="number" min="0" max="240" inputmode="numeric">
    </section>

    <section>
      <h2>Home Assistant</h2>
      <label for="haUrl">Adres</label>
      <input id="haUrl" placeholder="https://192.168.100.131:8123" autocomplete="off">
      <label for="s_ha_token">Token długoterminowy <span class="saved" id="has_ha_token"></span></label>
      <input id="s_ha_token" type="password" autocomplete="off" placeholder="Wklej token (puste = bez zmian, „-” = usuń)">
      <p class="hint">Token: HA → Twój profil → Bezpieczeństwo → Tokeny dostępu długoterminowego → Utwórz token. Po wpisaniu kliknij Zapisz na dole.</p>
      <p class="hint" id="haStatus"></p>
      <div id="haTrust" class="hidden">
        <p class="hint">Odcisk certyfikatu (SHA-256):</p>
        <code id="haFingerprint" class="fp"></code>
        <button type="button" id="haTrustBtn">Ufaj temu certyfikatowi</button>
      </div>
      <div id="haEntities" class="hidden">
        <label for="haWeather">Pogoda</label>
        <select id="haWeather"></select>
        <label for="haOutside">Temperatura na zewnątrz <span class="hint">(puste = z pogody)</span></label>
        <select id="haOutside"></select>
        <label for="haInside">Temperatura w domu</label>
        <select id="haInside"></select>
        <label>Osoby (liczone „w domu”)</label>
        <div id="haPersons" class="checks"></div>
        <h3>Światła na domku</h3>
        <p class="hint">Włączone światło rozjaśnia ten pokój na domku w tle.</p>
        <div id="haRooms"></div>
        <div class="row wrap">
          <button type="button" id="haSaveBtn">Zapisz ustawienia HA</button>
        </div>
        <p class="hint" id="haMsg"></p>
        <h3>Powiadomienia</h3>
        <p class="hint">W automatyzacji HA dodaj akcję „Wywołaj zdarzenie” (edytuj w YAML):</p>
        <pre>- event: dom_launcher_notify
  event_data:
    title: Pralka
    message: Pranie skończone
    camera: camera.furtka   # opcjonalnie – podgląd z kamery
    seconds: 15             # opcjonalnie – jak długo widać</pre>
        <p class="hint">Nad innymi aplikacjami (Netflix, YouTube…) dymek pojawi się, gdy włączona jest usługa „Dom – ekran główny”.</p>
        <label for="haCamera">Kamera do testu (opcjonalnie)</label>
        <select id="haCamera"></select>
        <button type="button" id="haTestBtn">Wyślij testowe powiadomienie na telewizor</button>
      </div>
    </section>

    <section>
      <h2>Emby (NAS)</h2>
      <label for="embyUrl">Adres serwera</label>
      <input id="embyUrl" placeholder="http://192.168.1.20:8096" autocomplete="off">
      <label for="s_emby_key">Klucz API <span class="saved" id="has_emby_key"></span></label>
      <input id="s_emby_key" type="password" autocomplete="off" placeholder="Puste = bez zmian, „-” = usuń">
    </section>

    <section>
      <h2>TMDB (seriale i filmy)</h2>
      <label for="s_tmdb_key">Klucz API (v3) albo token odczytu (v4) <span class="saved" id="has_tmdb_key"></span></label>
      <input id="s_tmdb_key" type="password" autocomplete="off" placeholder="Puste = bez zmian, „-” = usuń">
      <p class="hint">Darmowy klucz: themoviedb.org → Ustawienia → API. Po wpisaniu kliknij Zapisz na dole.</p>
    </section>

    <section>
      <h2>Wasze serwisy streamingowe</h2>
      <p class="hint" id="provMsg">Wczytuję…</p>
      <div id="provList" class="checks"></div>
    </section>

    <section>
      <h2>Szukaj serialu lub filmu</h2>
      <div class="row">
        <input id="q" type="search" placeholder="np. The Last of Us" autocomplete="off" aria-label="Tytuł">
        <button type="button" id="qBtn">Szukaj</button>
      </div>
      <p class="hint" id="qMsg"></p>
      <ul id="results" class="media"></ul>
    </section>

    <section>
      <h2>Obserwowane seriale</h2>
      <p class="hint">„Obejrzane do” to ostatni odcinek, który już widzieliście. S0 E0 = zaczynacie od początku.</p>
      <ul id="shows" class="media"></ul>
    </section>

    <section>
      <h2>Lista do obejrzenia</h2>
      <ul id="watchlist" class="media"></ul>
    </section>

    <section>
      <h2>Kopia zapasowa</h2>
      <p class="hint">Obserwowane seriale i lista do obejrzenia w jednym pliku.</p>
      <div class="row wrap">
        <button type="button" id="exportBtn">Pobierz kopię</button>
        <label class="filebtn">Wczytaj kopię<input id="importFile" type="file" accept="application/json,.json"></label>
      </div>
      <p class="hint" id="backupMsg"></p>
    </section>
  </div>
</main>

<div class="bar hidden" id="bar"><div class="inner">
  <span id="msg"></span>
  <button class="primary" type="button" id="saveBtn">Zapisz</button>
</div></div>

<script>
(function () {
  var SECRETS = ["ha_token", "emby_key", "tmdb_key"];
  var pin = "";
  var apps = [];
  var favorites = [];
  function el(id) { return document.getElementById(id); }
  function labelOf(pkg) {
    for (var i = 0; i < apps.length; i++) if (apps[i].pkg === pkg) return apps[i].label;
    return pkg;
  }
  function api(path, options) {
    options = options || {};
    options.headers = options.headers || {};
    options.headers["X-Pin"] = pin;
    return fetch(path, options).then(function (r) {
      return r.json().then(function (body) {
        if (!r.ok) throw new Error(body.error || ("Błąd " + r.status));
        return body;
      });
    });
  }
  function button(text, label, onClick) {
    var b = document.createElement("button");
    b.type = "button"; b.textContent = text; b.setAttribute("aria-label", label); b.onclick = onClick;
    return b;
  }
  function renderFavorites() {
    var list = el("favList");
    list.textContent = "";
    favorites.forEach(function (pkg, i) {
      var li = document.createElement("li");
      var name = document.createElement("span");
      name.textContent = (i + 1) + ". " + labelOf(pkg);
      li.appendChild(name);
      li.appendChild(button("↑", "W górę", function () { if (i > 0) { favorites.splice(i - 1, 0, favorites.splice(i, 1)[0]); renderFavorites(); } }));
      li.appendChild(button("↓", "W dół", function () { if (i < favorites.length - 1) { favorites.splice(i + 1, 0, favorites.splice(i, 1)[0]); renderFavorites(); } }));
      li.appendChild(button("✕", "Usuń", function () { favorites.splice(i, 1); renderFavorites(); }));
      list.appendChild(li);
    });
    var add = el("addApp");
    add.textContent = "";
    apps.forEach(function (a) {
      if (favorites.indexOf(a.pkg) >= 0) return;
      var o = document.createElement("option");
      o.value = a.pkg; o.textContent = a.label;
      add.appendChild(o);
    });
  }
  function node(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text !== undefined && text !== null) n.textContent = text;
    return n;
  }
  function act(text, onClick) {
    var b = node("button", "", text);
    b.type = "button";
    b.onclick = function () { b.disabled = true; Promise.resolve(onClick()).then(function () { b.disabled = false; }, function () { b.disabled = false; }); };
    return b;
  }
  function mediaRow(poster, title, meta) {
    var li = node("li");
    if (poster) { var img = node("img"); img.src = poster; img.alt = ""; img.loading = "lazy"; li.appendChild(img); }
    else li.appendChild(node("div", "noimg"));
    var info = node("div", "info");
    info.appendChild(node("div", "title", title));
    if (meta) info.appendChild(node("div", "meta", meta));
    var acts = node("div", "acts");
    info.appendChild(acts);
    li.appendChild(info);
    return { li: li, acts: acts };
  }
  function libraryAction(payload, msgEl, okText) {
    if (msgEl) msgEl.textContent = "Chwila…";
    return api("/api/library", { method: "POST", headers: { "Content-Type": "application/json; charset=utf-8" }, body: JSON.stringify(payload) })
      .then(function (lib) { renderLibrary(lib); if (msgEl) msgEl.textContent = okText || "Zapisano ✓"; if (lastQuery) search(lastQuery, true); })
      .catch(function (e) { if (msgEl) msgEl.textContent = e.message; });
  }
  function showStatus(s) {
    var parts = [];
    if (s.provider) parts.push(s.provider);
    if (s.unwatched > 0 && s.next) parts.push("dalej " + s.next.code + " (" + s.unwatched + " do obejrzenia)");
    else if (s.nextAir) parts.push("następny " + s.nextAir.code + (s.nextAir.date ? " – " + s.nextAir.date : ""));
    else if (s.ended) parts.push("zakończony");
    else parts.push("na bieżąco");
    return parts.join(" · ");
  }
  function renderLibrary(lib) {
    var shows = el("shows");
    shows.textContent = "";
    if (!lib.shows.length) shows.appendChild(node("li", "meta", "Jeszcze nic. Wyszukaj serial powyżej i kliknij Obserwuj."));
    lib.shows.forEach(function (s) {
      var r = mediaRow(s.poster, s.name, showStatus(s));
      var msg = node("div", "meta");
      r.acts.appendChild(node("span", "meta", "Obejrzane do S"));
      var se = node("input"); se.type = "number"; se.min = "0"; se.inputMode = "numeric"; se.value = s.watched.s; se.setAttribute("aria-label", "Sezon");
      r.acts.appendChild(se);
      r.acts.appendChild(node("span", "meta", "E"));
      var ep = node("input"); ep.type = "number"; ep.min = "0"; ep.inputMode = "numeric"; ep.value = s.watched.e; ep.setAttribute("aria-label", "Odcinek");
      r.acts.appendChild(ep);
      r.acts.appendChild(act("Zapisz", function () {
        return libraryAction({ action: "watched", id: s.id, s: parseInt(se.value || "0", 10), e: parseInt(ep.value || "0", 10) }, msg);
      }));
      if (s.unwatched > 0 && s.next) r.acts.appendChild(act("Obejrzany " + s.next.code, function () { return libraryAction({ action: "next", id: s.id }, msg); }));
      r.acts.appendChild(act("Usuń", function () {
        if (!confirm("Przestać obserwować „" + s.name + "”?")) return;
        return libraryAction({ action: "unfollow", id: s.id }, msg, "Usunięto");
      }));
      r.li.querySelector(".info").appendChild(msg);
      shows.appendChild(r.li);
    });
    var wl = el("watchlist");
    wl.textContent = "";
    if (!lib.watchlist.length) wl.appendChild(node("li", "meta", "Pusta. Dodasz tu filmy i seriale z wyszukiwarki albo z telewizora."));
    lib.watchlist.forEach(function (w) {
      var r = mediaRow(w.poster, w.title, (w.type === "MOVIE" ? "Film" : "Serial") + (w.provider ? " · " + w.provider : ""));
      r.acts.appendChild(act("Usuń z listy", function () { return libraryAction({ action: "unlist", id: w.id, type: w.type }, null); }));
      wl.appendChild(r.li);
    });
  }
  var lastQuery = "";
  function search(q, quiet) {
    lastQuery = q;
    if (!quiet) el("qMsg").textContent = "Szukam…";
    return api("/api/search", { method: "POST", headers: { "Content-Type": "application/json; charset=utf-8" }, body: JSON.stringify({ q: q }) })
      .then(function (res) {
        var list = el("results");
        list.textContent = "";
        if (res.error) { el("qMsg").textContent = res.error; return; }
        if (!quiet) el("qMsg").textContent = res.results.length ? "" : "Nic nie znalazłem.";
        res.results.forEach(function (it) {
          var r = mediaRow(it.poster, it.title, (it.type === "MOVIE" ? "Film" : "Serial") + (it.year ? " · " + it.year : ""));
          var msg = node("div", "meta");
          if (it.type === "TV") {
            if (it.followed) r.acts.appendChild(node("span", "saved", "Obserwujecie ✓"));
            else {
              r.acts.appendChild(act("Obserwuj – jesteśmy na bieżąco", function () { return libraryAction({ action: "follow", id: it.id, caughtUp: true }, msg, "Obserwujecie ✓"); }));
              r.acts.appendChild(act("Obserwuj – od początku", function () { return libraryAction({ action: "follow", id: it.id, caughtUp: false }, msg, "Obserwujecie ✓"); }));
            }
          }
          if (it.onList) r.acts.appendChild(node("span", "saved", "Na liście ✓"));
          else if (!it.followed) r.acts.appendChild(act("Do obejrzenia", function () { return libraryAction({ action: "list", id: it.id, type: it.type }, msg, "Dodano ✓"); }));
          r.li.querySelector(".info").appendChild(msg);
          list.appendChild(r.li);
        });
      })
      .catch(function (e) { el("qMsg").textContent = e.message; });
  }
  function loadProviders() {
    el("provMsg").textContent = "Wczytuję…";
    api("/api/providers").then(function (p) {
      var box = el("provList");
      box.textContent = "";
      el("provMsg").textContent = p.error || "Zaznacz serwisy, które macie. Z nich będą polecane filmy i seriale.";
      p.all.forEach(function (prov) {
        var lab = node("label");
        var cb = node("input"); cb.type = "checkbox"; cb.value = prov.id; cb.checked = p.selected.indexOf(prov.id) >= 0;
        cb.dataset.name = prov.name;
        cb.onchange = saveProviders;
        lab.appendChild(cb);
        lab.appendChild(document.createTextNode(prov.name));
        box.appendChild(lab);
      });
    }).catch(function (e) { el("provMsg").textContent = e.message; });
  }
  function saveProviders() {
    var chosen = [];
    el("provList").querySelectorAll("input:checked").forEach(function (cb) { chosen.push({ id: parseInt(cb.value, 10), name: cb.dataset.name }); });
    el("provMsg").textContent = "Zapisuję…";
    api("/api/providers", { method: "POST", headers: { "Content-Type": "application/json; charset=utf-8" }, body: JSON.stringify({ providers: chosen }) })
      .then(function () { el("provMsg").textContent = "Zapisano ✓ (" + chosen.length + ")"; })
      .catch(function (e) { el("provMsg").textContent = e.message; });
  }
  var haPolls = 0;
  function fillSelect(id, items, value, emptyLabel) {
    var sel = el(id);
    sel.textContent = "";
    var none = node("option", "", emptyLabel || "— brak —"); none.value = ""; sel.appendChild(none);
    items.forEach(function (it) {
      var o = node("option", "", it.name + "  (" + it.state + ")"); o.value = it.id;
      if (it.id === value) o.selected = true;
      sel.appendChild(o);
    });
    if (value && !items.some(function (it) { return it.id === value; })) {
      var missing = node("option", "", value + " (nie znaleziono)"); missing.value = value; missing.selected = true; sel.appendChild(missing);
    }
  }
  function loadHa() {
    api("/api/ha").then(function (h) {
      var st = h.status;
      el("haStatus").textContent = st.message || "";
      el("haTrust").classList.toggle("hidden", st.status !== "untrusted");
      if (st.status === "untrusted") el("haFingerprint").textContent = st.fingerprint;
      var connected = st.status === "connected";
      el("haEntities").classList.toggle("hidden", !connected);
      if ((st.status === "connecting" || (st.status === "off" && el("haUrl").value)) && haPolls < 15) { haPolls++; setTimeout(loadHa, 2000); return; }
      if (!connected) return;
      var c = h.config;
      fillSelect("haWeather", h.weather, c.weather);
      fillSelect("haOutside", h.temps, c.outsideTemp, "— z pogody —");
      fillSelect("haInside", h.temps, c.insideTemp);
      fillSelect("haCamera", h.cameras, "", "— bez obrazu —");
      var persons = el("haPersons");
      persons.textContent = "";
      if (!h.persons.length) persons.appendChild(node("span", "hint", "Brak encji person.* w HA."));
      h.persons.forEach(function (p) {
        var lab = node("label");
        var cb = node("input"); cb.type = "checkbox"; cb.value = p.id; cb.checked = c.persons.indexOf(p.id) >= 0;
        lab.appendChild(cb);
        lab.appendChild(document.createTextNode(p.name));
        persons.appendChild(lab);
      });
      var rooms = el("haRooms");
      rooms.textContent = "";
      h.rooms.forEach(function (r) {
        var row = node("div", "room");
        row.appendChild(node("span", "", r.name));
        var sel = node("select"); sel.id = "room_" + r.id; sel.dataset.room = r.id;
        sel.setAttribute("aria-label", r.name);
        row.appendChild(sel);
        rooms.appendChild(row);
        fillSelect(sel.id, h.lights, c.rooms[String(r.id)] || "");
      });
    }).catch(function (e) { el("haStatus").textContent = e.message; });
  }
  function saveHa() {
    var persons = [];
    el("haPersons").querySelectorAll("input:checked").forEach(function (cb) { persons.push(cb.value); });
    var rooms = {};
    el("haRooms").querySelectorAll("select").forEach(function (sel) { if (sel.value) rooms[sel.dataset.room] = sel.value; });
    el("haMsg").textContent = "Zapisuję…";
    api("/api/ha/config", { method: "POST", headers: { "Content-Type": "application/json; charset=utf-8" }, body: JSON.stringify({
      weather: el("haWeather").value, outsideTemp: el("haOutside").value, insideTemp: el("haInside").value,
      persons: persons, rooms: rooms
    }) }).then(function () { el("haMsg").textContent = "Zapisano ✓"; })
      .catch(function (e) { el("haMsg").textContent = e.message; });
  }
  function loadLibrary() {
    api("/api/library").then(renderLibrary).catch(function (e) { el("backupMsg").textContent = e.message; });
  }
  function load() {
    el("loginMsg").textContent = "Łączę…";
    api("/api/state").then(function (s) {
      apps = s.apps; favorites = s.favorites.slice();
      el("login").classList.add("hidden");
      el("form").classList.remove("hidden");
      el("bar").classList.remove("hidden");
      renderFavorites();
      el("idle").value = s.idleMinutes;
      el("haUrl").value = s.haUrl;
      el("embyUrl").value = s.embyUrl;
      SECRETS.forEach(function (name) {
        el("has_" + name).textContent = s.secrets[name] ? "zapisany ✓" : "";
        el("s_" + name).value = "";
      });
      loadProviders();
      loadLibrary();
      haPolls = 0;
      loadHa();
    }).catch(function (e) {
      el("login").classList.remove("hidden");
      el("loginMsg").textContent = e.message;
    });
  }
  el("haSaveBtn").onclick = saveHa;
  el("haTrustBtn").onclick = function () {
    el("haStatus").textContent = "Zapisuję…";
    api("/api/ha/trust", { method: "POST", headers: { "Content-Type": "application/json; charset=utf-8" }, body: JSON.stringify({ fingerprint: el("haFingerprint").textContent }) })
      .then(function () { haPolls = 0; setTimeout(loadHa, 1500); })
      .catch(function (e) { el("haStatus").textContent = e.message; });
  };
  el("haTestBtn").onclick = function () {
    el("haMsg").textContent = "Wysyłam…";
    api("/api/ha/test", { method: "POST", headers: { "Content-Type": "application/json; charset=utf-8" }, body: JSON.stringify({ camera: el("haCamera").value }) })
      .then(function () { el("haMsg").textContent = "Wysłano – spójrz na telewizor."; })
      .catch(function (e) { el("haMsg").textContent = e.message; });
  };
  el("qBtn").onclick = function () { var q = el("q").value.trim(); if (q) search(q); };
  el("q").addEventListener("keydown", function (e) { if (e.key === "Enter") { e.preventDefault(); el("qBtn").onclick(); } });
  el("exportBtn").onclick = function () {
    fetch("/api/export", { headers: { "X-Pin": pin } }).then(function (r) {
      if (!r.ok) throw new Error("Błąd " + r.status);
      return r.blob();
    }).then(function (blob) {
      var a = node("a");
      a.href = URL.createObjectURL(blob);
      a.download = "dom-biblioteka-" + new Date().toISOString().slice(0, 10) + ".json";
      document.body.appendChild(a); a.click(); a.remove();
      el("backupMsg").textContent = "Pobrano ✓";
    }).catch(function (e) { el("backupMsg").textContent = e.message; });
  };
  el("importFile").onchange = function () {
    var file = el("importFile").files[0];
    if (!file) return;
    if (!confirm("Wczytać kopię? Obecne seriale i lista zostaną zastąpione.")) { el("importFile").value = ""; return; }
    file.text().then(function (text) {
      return api("/api/import", { method: "POST", headers: { "Content-Type": "application/json; charset=utf-8" }, body: JSON.stringify({ json: text }) });
    }).then(function (lib) { renderLibrary(lib); el("backupMsg").textContent = "Wczytano ✓"; })
      .catch(function (e) { el("backupMsg").textContent = e.message; })
      .then(function () { el("importFile").value = ""; });
  };
  el("loginBtn").onclick = function () { pin = el("pin").value.replace(/\D/g, ""); load(); };
  el("addBtn").onclick = function () { var v = el("addApp").value; if (v) { favorites.push(v); renderFavorites(); } };
  el("saveBtn").onclick = function () {
    var secrets = {};
    SECRETS.forEach(function (name) { secrets[name] = el("s_" + name).value.trim(); });
    var body = {
      favorites: favorites,
      idleMinutes: parseInt(el("idle").value || "0", 10),
      haUrl: el("haUrl").value,
      embyUrl: el("embyUrl").value,
      secrets: secrets
    };
    el("msg").textContent = "Zapisuję…";
    api("/api/save", { method: "POST", headers: { "Content-Type": "application/json; charset=utf-8" }, body: JSON.stringify(body) })
      .then(function () { el("msg").textContent = "Zapisano ✓"; load(); })
      .catch(function (e) { el("msg").textContent = e.message; });
  };
  var match = /pin=(\d{6})/.exec(location.hash);
  if (match) { pin = match[1]; el("pin").value = pin; history.replaceState(null, "", "/"); load(); }
})();
</script>
</body>
</html>
"""
