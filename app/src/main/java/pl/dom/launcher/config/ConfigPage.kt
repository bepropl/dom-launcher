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
      <h2>Przycisk pilota</h2>
      <p class="hint" id="remapInfo"></p>
      <label for="remapAction">Co ma robić</label>
      <select id="remapAction">
        <option value="NONE">Nic (działa jak zwykle)</option>
        <option value="OPEN_LAUNCHER">Otwiera launcher</option>
        <option value="OPEN_APP">Otwiera aplikację…</option>
      </select>
      <div id="remapAppBox" class="hidden">
        <label for="remapApp">Aplikacja</label>
        <select id="remapApp"></select>
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
      <input id="haUrl" placeholder="http://192.168.1.10:8123" autocomplete="off">
      <label for="s_ha_token">Token długoterminowy <span class="saved" id="has_ha_token"></span></label>
      <input id="s_ha_token" type="password" autocomplete="off" placeholder="Wklej token (puste = bez zmian, „-” = usuń)">
    </section>

    <section>
      <h2>Emby (NAS)</h2>
      <label for="embyUrl">Adres serwera</label>
      <input id="embyUrl" placeholder="http://192.168.1.20:8096" autocomplete="off">
      <label for="s_emby_key">Klucz API <span class="saved" id="has_emby_key"></span></label>
      <input id="s_emby_key" type="password" autocomplete="off" placeholder="Puste = bez zmian, „-” = usuń">
    </section>

    <section>
      <h2>TMDB i Trakt</h2>
      <label for="s_tmdb_key">TMDB – klucz API <span class="saved" id="has_tmdb_key"></span></label>
      <input id="s_tmdb_key" type="password" autocomplete="off" placeholder="Puste = bez zmian, „-” = usuń">
      <label for="s_trakt_client_id">Trakt – Client ID <span class="saved" id="has_trakt_client_id"></span></label>
      <input id="s_trakt_client_id" type="password" autocomplete="off" placeholder="Puste = bez zmian, „-” = usuń">
      <label for="s_trakt_client_secret">Trakt – Client Secret <span class="saved" id="has_trakt_client_secret"></span></label>
      <input id="s_trakt_client_secret" type="password" autocomplete="off" placeholder="Puste = bez zmian, „-” = usuń">
      <p class="hint">Połączenie z kontem Trakt (kod do zatwierdzenia) dojdzie w etapie 4.</p>
    </section>
  </div>
</main>

<div class="bar hidden" id="bar"><div class="inner">
  <span id="msg"></span>
  <button class="primary" type="button" id="saveBtn">Zapisz</button>
</div></div>

<script>
(function () {
  var SECRETS = ["ha_token", "emby_key", "tmdb_key", "trakt_client_id", "trakt_client_secret"];
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
  function fillAppSelect(select, selected) {
    select.textContent = "";
    apps.forEach(function (a) {
      var o = document.createElement("option");
      o.value = a.pkg; o.textContent = a.label;
      if (a.pkg === selected) o.selected = true;
      select.appendChild(o);
    });
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
      el("remapInfo").textContent = s.remap.keyCode >= 0
        ? "Przypisany przycisk: " + s.remap.keyName + ". Inny przycisk przypiszesz na telewizorze."
        : "Najpierw na telewizorze wybierz „Przypisz przycisk” i wciśnij go na pilocie.";
      el("remapAction").value = s.remap.action;
      fillAppSelect(el("remapApp"), s.remap.pkg);
      el("remapAppBox").classList.toggle("hidden", s.remap.action !== "OPEN_APP");
      SECRETS.forEach(function (name) {
        el("has_" + name).textContent = s.secrets[name] ? "zapisany ✓" : "";
        el("s_" + name).value = "";
      });
    }).catch(function (e) {
      el("login").classList.remove("hidden");
      el("loginMsg").textContent = e.message;
    });
  }
  el("loginBtn").onclick = function () { pin = el("pin").value.replace(/\D/g, ""); load(); };
  el("addBtn").onclick = function () { var v = el("addApp").value; if (v) { favorites.push(v); renderFavorites(); } };
  el("remapAction").onchange = function () { el("remapAppBox").classList.toggle("hidden", el("remapAction").value !== "OPEN_APP"); };
  el("saveBtn").onclick = function () {
    var secrets = {};
    SECRETS.forEach(function (name) { secrets[name] = el("s_" + name).value.trim(); });
    var body = {
      favorites: favorites,
      idleMinutes: parseInt(el("idle").value || "0", 10),
      haUrl: el("haUrl").value,
      embyUrl: el("embyUrl").value,
      remap: { action: el("remapAction").value, pkg: el("remapApp").value || "" },
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
