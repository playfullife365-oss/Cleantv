#!/bin/bash
# CleanTV Speed for the Mac: checks how this Mac is connected (Wi-Fi band, signal and link rate, or Ethernet),
# measures ping, jitter, download and upload against speed.cloudflare.com, saves the result, and opens a
# dashboard of every test so far. Double-click it in Finder, or run it in Terminal.
#
#   bash "CleanTV Speed.command" --install   copy it to the Desktop, ready to double-click
#   bash "CleanTV Speed.command" --wifi      only show the connection, no test
#   bash "CleanTV Speed.command" --diagnose  show the connection and what's slowing it down, no speed test
#   bash "CleanTV Speed.command" --dashboard only open the dashboard
#
# Uses only what every Mac has (bash, curl, awk). Results stay on this Mac, in
# ~/Library/Application Support/CleanTV Speed.

set -u

SERVER="${SPEED_SERVER:-https://speed.cloudflare.com}"
SECONDS_PER_PHASE="${SPEED_SECONDS:-10}"
STREAMS=4
PINGS=10
DATA_DIR="${SPEED_DATA_DIR:-$HOME/Library/Application Support/CleanTV Speed}"
HISTORY="$DATA_DIR/history.csv"
DASHBOARD="$DATA_DIR/dashboard.html"
KEEP=100

bold=$'\033[1m'; dim=$'\033[2m'; blue=$'\033[38;5;33m'; orange=$'\033[38;5;166m'; reset=$'\033[0m'

install_to_desktop() {
    local target="$HOME/Desktop/CleanTV Speed.command"
    cp "$0" "$target" && chmod +x "$target"
    # Downloaded files are quarantined, which makes macOS refuse to open them with a double-click.
    xattr -d com.apple.quarantine "$target" 2>/dev/null
    echo "Installed: $target"
    echo "Double-click it on your Desktop to test your connection."
}

# ---------------------------------------------------------------------------------------------------------------
# Connection
# ---------------------------------------------------------------------------------------------------------------

# Sets CONN_KIND (wifi, ethernet, other or none), CONN_NAME, CONN_BAND, CONN_PHY, CONN_RSSI, CONN_NOISE, CONN_RATE.
read_connection() {
    CONN_KIND=none; CONN_NAME=""; CONN_BAND=""; CONN_PHY=""; CONN_RSSI=""; CONN_NOISE=""; CONN_RATE=""
    CONN_CHANNEL=""; CONN_WIDTH=""; CONN_CROWD=0; CONN_NEIGHBOURS=0; CONN_MAC_BEST=""; CONN_GATEWAY=""
    local iface wifi_iface
    iface=$(route -n get default 2>/dev/null | awk '/interface:/ {print $2}')
    CONN_GATEWAY=$(route -n get default 2>/dev/null | awk '/gateway:/ {print $2}')
    [ -z "$iface" ] && return
    wifi_iface=$(networksetup -listallhardwareports 2>/dev/null \
        | awk '/Hardware Port: (Wi-Fi|AirPort)/ {getline; print $2; exit}')
    if [ -n "$wifi_iface" ] && [ "$iface" = "$wifi_iface" ]; then
        CONN_KIND=wifi
        parse_wifi "$(system_profiler SPAirPortDataType 2>/dev/null)"
    elif networksetup -listallhardwareports 2>/dev/null | grep -B1 "Device: $iface\$" | grep -qiE 'ethernet|thunderbolt|usb.*lan'; then
        CONN_KIND=ethernet
    else
        CONN_KIND=other
    fi
}

# Reads the current network out of system_profiler's Wi-Fi report, plus the neighbouring networks it can hear.
parse_wifi() {
    local parsed
    parsed=$(printf '%s\n' "$1" | awk '
        /Supported PHY Modes:/ { supported = $0 }
        /Current Network Information:/ { inside = 1; getline; name = $0; sub(/^[ \t]+/, "", name); sub(/:$/, "", name); gsub(/\|/, " ", name); next }
        /Other Local Wi-Fi Networks:/ { inside = 0; others = 1 }
        inside && /PHY Mode:/ { phy = $NF }
        inside && /Channel:/ {
            channel = $2
            # macOS writes the 2.4 GHz band as "2GHz".
            if (match($0, /[0-9.]+GHz/)) { band = substr($0, RSTART, RLENGTH - 3); if (band == "2") band = "2.4"; band = band " GHz" }
            if (match($0, /[0-9]+MHz/)) width = substr($0, RSTART, RLENGTH - 3)
        }
        inside && /Signal \/ Noise:/ { rssi = $4; noise = $7 }
        inside && /Transmit Rate:/ { rate = $NF }
        others && /Channel:/ { neighbour[++n] = $2 }
        END {
            for (i = 1; i <= n; i++) {
                if (neighbour[i] == channel) same++
                # On 2.4 GHz, channels closer than 5 apart overlap and interfere.
                else if (band == "2.4 GHz" && neighbour[i] < 15 && channel < 15 && (neighbour[i] - channel < 5 && channel - neighbour[i] < 5)) same++
            }
            printf "%s|%s|%s|%s|%s|%s|%s|%s|%d|%d|%s\n", name, band, phy, rssi, noise, rate, channel, width, same, n,
                (supported ~ /ax/ ? "ax" : supported ~ /ac/ ? "ac" : "")
        }')
    IFS='|' read -r CONN_NAME CONN_BAND CONN_PHY CONN_RSSI CONN_NOISE CONN_RATE CONN_CHANNEL CONN_WIDTH \
        CONN_CROWD CONN_NEIGHBOURS CONN_MAC_BEST <<< "$parsed"
    # Recent macOS hides the network name from apps without location access.
    case "$CONN_NAME" in *redacted*|"") CONN_NAME="" ;; esac
}

wifi_generation() {
    case "$1" in
        802.11n) echo "Wi-Fi 4" ;; 802.11ac) echo "Wi-Fi 5" ;; 802.11ax) echo "Wi-Fi 6" ;; 802.11be) echo "Wi-Fi 7" ;;
    esac
}

signal_name() {
    local r=$1
    if   [ "$r" -ge -55 ]; then echo "Excellent"
    elif [ "$r" -ge -67 ]; then echo "Good"
    elif [ "$r" -ge -75 ]; then echo "Fair"
    elif [ "$r" -ge -85 ]; then echo "Weak"
    else echo "Very weak"; fi
}

show_connection() {
    echo "${bold}Connection${reset}"
    case "$CONN_KIND" in
        none)
            echo "  Not connected. Turn on Wi-Fi from the menu bar and pick your network, then try again."
            echo "  If the router's INTERNET light is off, restart the router first."
            return 1 ;;
        ethernet) echo "  Ethernet (wired)" ;;
        other) echo "  Connected (not Wi-Fi or Ethernet)" ;;
        wifi)
            local line="  Wi-Fi" gen
            [ -n "$CONN_NAME" ] && line="$line \"$CONN_NAME\""
            [ -n "$CONN_BAND" ] && line="$line · $CONN_BAND"
            gen=$(wifi_generation "$CONN_PHY"); [ -n "$gen" ] && line="$line · $gen"
            echo "$line"
            if [[ "$CONN_RSSI" =~ ^-?[0-9]+$ ]]; then
                local detail="  Signal: $(signal_name "$CONN_RSSI") ($CONN_RSSI dBm)"
                [[ "$CONN_NOISE" =~ ^-?[0-9]+$ ]] && detail="$detail, noise $CONN_NOISE dBm"
                [[ "$CONN_RATE" =~ ^[0-9]+$ ]] && detail="$detail · Link $CONN_RATE Mbps"
                echo "$detail"
            fi
            ;;
    esac
}

# ---------------------------------------------------------------------------------------------------------------
# Speed test
# ---------------------------------------------------------------------------------------------------------------

measure_ping() {
    local samples=() out i
    for ((i = 0; i < PINGS; i++)); do
        # One round trip: from the end of the TLS handshake to the first byte of the answer.
        out=$(curl -s -o /dev/null --max-time 5 -w '%{http_code} %{time_appconnect} %{time_connect} %{time_starttransfer}' \
            "$SERVER/__down?bytes=0") || continue
        set -- $out
        [ "$1" = "200" ] || continue
        samples+=("$(awk -v a="$2" -v c="$3" -v s="$4" 'BEGIN { base = (a > 0 ? a : c); printf "%.2f", (s - base) * 1000 }')")
    done
    [ ${#samples[@]} -eq 0 ] && return 1
    printf '%s\n' "${samples[@]}" | awk '
        { v[NR] = $1 }
        END {
            n = asort_copy()
            for (i = 2; i <= NR; i++) { d = v[i] - v[i-1]; j += (d < 0 ? -d : d) }
            printf "%.2f %.2f\n", median, (NR > 1 ? j / (NR - 1) : 0)
        }
        function asort_copy(   i, k, t, s) {
            for (i = 1; i <= NR; i++) s[i] = v[i]
            for (i = 2; i <= NR; i++) { t = s[i]; for (k = i - 1; k >= 1 && s[k] > t; k--) s[k+1] = s[k]; s[k+1] = t }
            median = (NR % 2 ? s[(NR + 1) / 2] : (s[NR / 2] + s[NR / 2 + 1]) / 2)
            return NR
        }'
}

# Runs STREAMS curls at once for SECONDS_PER_PHASE and prints Mbps from the bytes they moved.
measure_transfer() {
    local direction=$1 work i
    work=$(mktemp -d)
    if [ "$direction" = up ]; then
        # Random data, so nothing on the way can compress it.
        head -c 50000000 /dev/urandom > "$work/payload"
    fi
    for ((i = 0; i < STREAMS; i++)); do
        if [ "$direction" = down ]; then
            curl -s -o /dev/null --max-time "$SECONDS_PER_PHASE" -w '%{size_download} %{time_total}\n' \
                "$SERVER/__down?bytes=1000000000" > "$work/$i" &
        else
            curl -s -o /dev/null --max-time "$SECONDS_PER_PHASE" -w '%{size_upload} %{time_total}\n' \
                -H 'Content-Type: application/octet-stream' --data-binary @"$work/payload" \
                "$SERVER/__up" > "$work/$i" &
        fi
    done
    wait
    cat "$work"/[0-9]* 2>/dev/null | awk '
        { bytes += $1; if ($2 > t) t = $2 }
        END { if (bytes > 0 && t > 0) printf "%.2f\n", bytes * 8 / t / 1000000; else exit 1 }'
    local status=$?
    rm -rf "$work"
    return $status
}

# ---------------------------------------------------------------------------------------------------------------
# Diagnosis: what is slowing things down, and what to do about it
# ---------------------------------------------------------------------------------------------------------------

# Prints "<average ms> <loss %>" for a few pings to $1, or nothing when ping can't run.
ping_stats() {
    ping -c 5 "$1" 2>/dev/null | awk '
        /packet loss/ { if (match($0, /[0-9.]+% packet loss/)) loss = substr($0, RSTART, RLENGTH - 13) }
        /min\/avg\/max/ { split($0, parts, "="); split(parts[2], v, "/"); avg = v[2] + 0 }
        END { if (loss != "") printf "%.1f %s\n", avg, loss }'
}

FINDINGS=0
finding() {
    FINDINGS=$((FINDINGS + 1))
    echo "  ${bold}$FINDINGS. $1${reset}"
    shift
    local line
    for line in "$@"; do echo "     $line"; done
}

# Above, at or below a threshold, with decimals; awk because bash only compares whole numbers.
above() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a + 0 > b + 0) }'; }

diagnose() {
    echo
    echo "${bold}What's slowing it down${reset}"
    FINDINGS=0
    local router_url="your router's settings"
    [ -n "$CONN_GATEWAY" ] && router_url="your router's settings at http://$CONN_GATEWAY (password on its label)"

    if [ "$CONN_KIND" = none ]; then
        finding "This Mac isn't connected" "Pick your network from the Wi-Fi menu in the menu bar."
        return
    fi

    # Where is the delay: between this Mac and the router, or beyond the router?
    local router="" internet="" r_avg r_loss i_avg i_loss
    [ -n "$CONN_GATEWAY" ] && router=$(ping_stats "$CONN_GATEWAY")
    internet=$(ping_stats 1.1.1.1)
    read -r r_avg r_loss <<< "${router:-}"
    read -r i_avg i_loss <<< "${internet:-}"
    if [ -n "$router" ]; then
        echo "  ${dim}To the router: ${r_avg} ms, ${r_loss}% lost · To the internet: ${i_avg:-?} ms, ${i_loss:-?}% lost${reset}"
    fi

    if [ -n "$router" ] && { [ -z "$internet" ] || above "${i_loss:-100}" 99; } && ! above "$r_loss" 99; then
        finding "The router is reachable but the internet isn't" \
            "The problem is the line into the house, not Wi-Fi. On the Zyxel, check the INTERNET light:" \
            "if it's off, unplug the router for 30 seconds and plug it back in. If it stays off, call your provider."
    elif [ -n "$router" ] && { above "$r_loss" 0 || above "$r_avg" 15; }; then
        finding "The Wi-Fi link to the router is unreliable (${r_avg} ms, ${r_loss}% lost; a good link is under 5 ms, 0% lost)" \
            "This is the Wi-Fi itself. The fixes below about signal, band and channel are the ones that matter."
    elif [ -n "$internet" ] && { above "$i_loss" 1 || above "$i_avg" 80; }; then
        finding "The connection beyond the router is slow (${i_avg} ms, ${i_loss}% lost)" \
            "Wi-Fi is fine; the delay is on your provider's side. Restart the router, and if it keeps happening," \
            "report it to your provider with these figures."
    fi

    if [ "$CONN_KIND" = wifi ]; then
        if [[ "$CONN_RSSI" =~ ^-[0-9]+$ ]] && [ "$CONN_RSSI" -lt -67 ]; then
            finding "Signal on the weak side ($CONN_RSSI dBm; -67 or better is good)" \
                "Move the router out of cabinets and away from metal, TVs and microwaves, ideally up high and central." \
                "Or bring the Mac closer, use an Ethernet cable, or add a mesh point between them."
        fi
        if [[ "$CONN_RSSI" =~ ^-[0-9]+$ ]] && [[ "$CONN_NOISE" =~ ^-[0-9]+$ ]] && [ $((CONN_RSSI - CONN_NOISE)) -lt 25 ]; then
            finding "Lots of interference (signal only $((CONN_RSSI - CONN_NOISE)) dB above the noise; 25+ is good)" \
                "Keep the router away from microwaves, cordless phones, baby monitors and Bluetooth hubs," \
                "and try a different channel (below)."
        fi
        if [ "$CONN_BAND" = "2.4 GHz" ]; then
            finding "On the slow 2.4 GHz band" \
                "Your Zyxel also broadcasts 5 GHz, which is several times faster within a room or two." \
                "If both bands share one name, the Mac picks; otherwise choose the 5 GHz network in the Wi-Fi menu," \
                "or in $router_url, give both bands the same name so devices move to 5 GHz on their own."
        fi
        if [ "${CONN_CROWD:-0}" -ge 3 ]; then
            finding "Crowded channel ($CONN_CROWD nearby networks share channel ${CONN_CHANNEL})" \
                "In $router_url, under Wireless, set the channel to Auto, or pick one the neighbours don't use" \
                "(on 2.4 GHz only 1, 6 or 11)."
        fi
        if [ "$CONN_BAND" != "2.4 GHz" ] && [ -n "$CONN_WIDTH" ] && [ "$CONN_WIDTH" -lt 80 ]; then
            finding "Narrow ${CONN_WIDTH} MHz channel on ${CONN_BAND:-5 GHz}" \
                "In $router_url, under Wireless → 5 GHz, set the bandwidth to 80 MHz. That roughly doubles the top speed."
        fi
        if [ "$CONN_MAC_BEST" = ax ] && [ -n "$CONN_PHY" ] && [ "$CONN_PHY" != "802.11ax" ] && [ "$CONN_PHY" != "802.11be" ]; then
            finding "Connected with older Wi-Fi ($CONN_PHY) though both the Mac and the Zyxel T-56 support Wi-Fi 6" \
                "In $router_url, under Wireless, make sure 802.11ax (Wi-Fi 6) mode is on."
        fi
    fi

    if [ -n "${RESULT_DOWN:-}" ] && [ "$CONN_KIND" = wifi ] && [[ "$CONN_RATE" =~ ^[0-9]+$ ]] && [ "$FINDINGS" -eq 0 ] \
        && awk -v d="$RESULT_DOWN" -v r="$CONN_RATE" 'BEGIN { exit !(d < r * 0.25) }'; then
        finding "Wi-Fi looks healthy, but the speed is well under what it could carry" \
            "That points at your internet plan or the line. Compare $(fmt "$RESULT_DOWN") Mbps with the speed you pay for;" \
            "test again at a quiet hour, and check other devices aren't streaming or downloading meanwhile."
    fi

    if [ "$FINDINGS" -eq 0 ]; then
        echo "  Nothing wrong found. Signal, band, channel and the link to the router all look good."
    fi
    echo "  ${dim}Also worth doing: restart the router now and then, and keep its firmware up to date.${reset}"
}

fmt() {
    awk -v v="$1" 'BEGIN { if (v == "") print "—"; else if (v < 10) printf "%.1f", v; else printf "%.0f", v }'
}

run_test() {
    echo
    echo "${bold}Speed test${reset} ${dim}(speed.cloudflare.com, about 25 seconds)${reset}"
    local ping_line ping="" jitter="" down="" up=""
    printf '  Ping      … '
    if ping_line=$(measure_ping); then
        read -r ping jitter <<< "$ping_line"
        echo "$(fmt "$ping") ms  ${dim}(jitter $(fmt "$jitter") ms)${reset}"
    else
        echo "couldn't reach the test server"
    fi
    printf "  ${blue}Download${reset}  … "
    down=$(measure_transfer down) && echo "${bold}$(fmt "$down")${reset} Mbps" || { down=""; echo "failed"; }
    printf "  ${orange}Upload${reset}    … "
    up=$(measure_transfer up) && echo "${bold}$(fmt "$up")${reset} Mbps" || { up=""; echo "failed"; }

    if [ -z "$ping$down$up" ]; then
        echo
        echo "  No results. Check that this Mac is online (try opening a web page)."
        echo "  If the router's INTERNET light is off, the router itself isn't online: restart it."
        return 1
    fi
    if [ "$CONN_KIND" = wifi ] && [[ "$CONN_RATE" =~ ^[0-9]+$ ]] && [ -n "$down" ]; then
        echo "  ${dim}Wi-Fi link to the router: $CONN_RATE Mbps. Over Wi-Fi, real speed is usually about half of that.${reset}"
    fi
    RESULT_PING=$ping; RESULT_JITTER=$jitter; RESULT_DOWN=$down; RESULT_UP=$up
    save_result "$ping" "$jitter" "$down" "$up"
}

save_result() {
    mkdir -p "$DATA_DIR"
    local name=${CONN_NAME//,/ }
    printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' "$(date +%s)" "$3" "$4" "$1" "$2" "$CONN_KIND" "$CONN_BAND" \
        "$CONN_PHY" "$CONN_RSSI" "$CONN_RATE" "$name" >> "$HISTORY"
    # Keep the newest tests only.
    tail -n "$KEEP" "$HISTORY" > "$HISTORY.tmp" && mv "$HISTORY.tmp" "$HISTORY"
}

# ---------------------------------------------------------------------------------------------------------------
# Dashboard: a self-contained page with the history inlined, so it opens from disk with no server.
# ---------------------------------------------------------------------------------------------------------------

write_dashboard() {
    mkdir -p "$DATA_DIR"
    {
        sed -n '/^#DASHBOARD-HEAD$/,/^#DASHBOARD-DATA$/p' "$0" | sed '1d;$d'
        echo "const TESTS = ["
        if [ -f "$HISTORY" ]; then
            awk -F, '{
                gsub(/["\\<>]/, "", $11)
                printf "{t:%s,down:%s,up:%s,ping:%s,jitter:%s,kind:\"%s\",band:\"%s\",phy:\"%s\",rssi:%s,rate:%s,name:\"%s\"},\n",
                    $1, num($2), num($3), num($4), num($5), $6, $7, $8, num($9), num($10), $11
            }
            function num(v) { return v ~ /^-?[0-9.]+$/ ? v : "null" }' "$HISTORY"
        fi
        echo "];"
        sed -n '/^#DASHBOARD-DATA$/,/^#DASHBOARD-END$/p' "$0" | sed '1d;$d'
    } > "$DASHBOARD"
}

case "${1:-}" in
    --install) install_to_desktop; exit ;;
    --wifi) read_connection; show_connection; exit ;;
    --diagnose) read_connection; show_connection; diagnose; exit ;;
    --dashboard) write_dashboard; open "$DASHBOARD" 2>/dev/null || echo "Dashboard: $DASHBOARD"; exit ;;
esac

clear 2>/dev/null
echo "${bold}CleanTV Speed${reset}"
echo
read_connection
if show_connection; then
    run_test
fi
diagnose
write_dashboard
echo
echo "Opening the dashboard…"
open "$DASHBOARD" 2>/dev/null || echo "Dashboard: $DASHBOARD"
exit 0

# The dashboard page, copied out by write_dashboard. The heredoc keeps bash from reading it as commands.
: <<'#DASHBOARD-END'
#DASHBOARD-HEAD
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>CleanTV Speed</title>
<style>
  :root {
    --bg: #f6f6f4; --surface: #ffffff; --border: #e4e3de; --text: #1b1b1a; --muted: #6b6a64; --grid: #ecebe6;
    --down: #2a78d6; --up: #eb6834; --ping: #8a6d00;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg: #111214; --surface: #1a1a19; --border: #2c2c2a; --text: #f0efec; --muted: #a3a29a; --grid: #2a2a28;
      --down: #3987e5; --up: #d95926; --ping: #c98500;
    }
  }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--bg); color: var(--text);
         font: 15px/1.45 -apple-system, BlinkMacSystemFont, "Helvetica Neue", sans-serif; }
  main { max-width: 1100px; margin: 0 auto; padding: 32px 20px 48px; }
  h1 { font-size: 28px; margin: 0; }
  h2 { font-size: 17px; margin: 0 0 12px; }
  .sub { color: var(--muted); margin: 4px 0 0; }
  .card { background: var(--surface); border: 1px solid var(--border); border-radius: 14px; padding: 18px 20px; }
  .kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin: 24px 0; }
  .kpi .name { color: var(--muted); display: flex; align-items: center; gap: 8px; }
  .dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; flex: none; }
  .kpi .value { font-size: 34px; font-weight: 300; font-variant-numeric: tabular-nums; }
  .kpi .unit { color: var(--muted); font-size: 15px; margin-left: 4px; }
  .kpi .detail { color: var(--muted); font-size: 13px; }
  .chart-head { display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: 8px; }
  .legend { display: flex; gap: 16px; color: var(--muted); font-size: 14px; align-items: center; }
  .legend span { display: inline-flex; align-items: center; gap: 6px; }
  svg { display: block; width: 100%; height: auto; overflow: visible; }
  .tip { position: fixed; pointer-events: none; background: var(--surface); border: 1px solid var(--border);
         border-radius: 8px; padding: 8px 10px; font-size: 13px; box-shadow: 0 4px 16px rgba(0,0,0,.15);
         display: none; font-variant-numeric: tabular-nums; white-space: nowrap; }
  .tip b { display: block; margin-bottom: 2px; }
  .scroll { overflow-x: auto; }
  table { width: 100%; border-collapse: collapse; font-variant-numeric: tabular-nums; }
  th { color: var(--muted); font-weight: 500; font-size: 13px; text-align: left; padding: 6px 8px; border-bottom: 1px solid var(--border); }
  td { padding: 7px 8px; border-bottom: 1px solid var(--grid); }
  th.n, td.n { text-align: right; }
  .stack { display: grid; grid-template-columns: minmax(0, 1fr); gap: 14px; margin-top: 14px; }
  .note { color: var(--muted); font-size: 13px; margin-top: 10px; }
  .empty { color: var(--muted); font-size: 18px; margin-top: 40px; }
  @media (max-width: 760px) { .kpis { grid-template-columns: repeat(2, 1fr); } .hide-sm { display: none; } }
</style>
</head>
<body>
<main>
  <h1>CleanTV Speed</h1>
  <p class="sub" id="latest-conn"></p>
  <div id="app"></div>
</main>
<div class="tip" id="tip"></div>
<script>
#DASHBOARD-DATA
const app = document.getElementById("app");
const fmt = v => v == null ? "—" : v === 0 ? "0" : v < 10 ? v.toFixed(1) : Math.round(v).toString();
const when = t => new Date(t * 1000).toLocaleString(undefined, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
const day = t => new Date(t * 1000).toLocaleDateString(undefined, { month: "short", day: "numeric" });
const gen = { "802.11n": "Wi-Fi 4", "802.11ac": "Wi-Fi 5", "802.11ax": "Wi-Fi 6", "802.11be": "Wi-Fi 7" };
const esc = s => String(s).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
function label(t) {
  if (t.kind === "ethernet") return "Ethernet";
  if (t.kind === "other") return "Other network";
  if (t.kind === "none") return "Not connected";
  return ["Wi-Fi", t.band, gen[t.phy]].filter(Boolean).join(" · ");
}
function summary(key, higherIsBetter) {
  const v = TESTS.map(t => t[key]).filter(x => x != null);
  if (!v.length) return null;
  return { latest: v[v.length - 1], avg: v.reduce((a, b) => a + b, 0) / v.length,
           best: higherIsBetter ? Math.max(...v) : Math.min(...v) };
}
function kpi(name, color, s, unit) {
  return `<div class="card kpi"><div class="name">${color ? `<span class="dot" style="background:${color}"></span>` : ""}${name}</div>
    <div><span class="value">${s ? fmt(s.latest) : "—"}</span><span class="unit">${unit}</span></div>
    <div class="detail">${s ? `Avg ${fmt(s.avg)} · Best ${fmt(s.best)}` : "&nbsp;"}</div></div>`;
}
function niceCeil(max) {
  if (!(max > 1)) return 1;
  const m = Math.pow(10, Math.floor(Math.log10(max)));
  for (const s of [1, 2, 5, 10]) if (s * m >= max - 1e-9) return s * m;
  return 10 * m;
}
function chart(tests) {
  const W = 1000, H = 300, L = 48, R = 92, T = 12, B = 30;
  const pw = W - L - R, ph = H - T - B;
  const top = niceCeil(Math.max(0, ...tests.flatMap(t => [t.down, t.up]).filter(v => v != null)));
  const x = i => tests.length < 2 ? L + pw / 2 : L + pw * i / (tests.length - 1);
  const y = v => T + ph - ph * Math.min(1, v / top);
  let g = "";
  for (let i = 0; i <= 4; i++) {
    const gy = T + ph - ph * i / 4;
    g += `<line x1="${L}" x2="${L + pw}" y1="${gy}" y2="${gy}" stroke="var(--grid)" stroke-width="1"/>
          <text x="${L - 8}" y="${gy}" text-anchor="end" dominant-baseline="middle" fill="var(--muted)" font-size="12">${fmt(top * i / 4)}</text>`;
  }
  g += `<text x="${L}" y="${H - 6}" fill="var(--muted)" font-size="12">${day(tests[0].t)}</text>`;
  if (tests.length > 1) g += `<text x="${L + pw}" y="${H - 6}" text-anchor="end" fill="var(--muted)" font-size="12">${day(tests[tests.length - 1].t)}</text>`;
  const ends = [];
  for (const [key, color, name] of [["down", "var(--down)", "Download"], ["up", "var(--up)", "Upload"]]) {
    let d = "", pen = false, last = null;
    tests.forEach((t, i) => {
      if (t[key] == null) { pen = false; return; }
      d += `${pen ? "L" : "M"}${x(i).toFixed(1)},${y(t[key]).toFixed(1)}`; pen = true; last = y(t[key]);
    });
    g += `<path d="${d}" fill="none" stroke="${color}" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>`;
    if (tests.length === 1 && tests[0][key] != null) g += `<circle cx="${x(0)}" cy="${y(tests[0][key])}" r="4" fill="${color}" stroke="var(--surface)" stroke-width="2"/>`;
    if (last != null) ends.push({ y: last, color, name });
  }
  if (ends.length === 2 && Math.abs(ends[0].y - ends[1].y) < 16) {
    const mid = (ends[0].y + ends[1].y) / 2, a = ends[0].y <= ends[1].y ? -8 : 8;
    ends[0].y = mid + a; ends[1].y = mid - a;
  }
  for (const e of ends) g += `<circle cx="${L + pw + 10}" cy="${e.y}" r="3" fill="${e.color}"/>
    <text x="${L + pw + 18}" y="${e.y}" dominant-baseline="middle" fill="var(--text)" font-size="13">${e.name}</text>`;
  g += `<line id="cursor" x1="0" x2="0" y1="${T}" y2="${T + ph}" stroke="var(--muted)" stroke-width="1" visibility="hidden"/>
        <g id="marks"></g><rect id="hit" x="${L - 10}" y="0" width="${pw + 20}" height="${H}" fill="transparent"/>`;
  return { svg: `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Download and upload speed over the last ${tests.length} tests">${g}</svg>`, x, y, L, pw };
}
function render() {
  if (!TESTS.length) { app.innerHTML = `<p class="empty">No tests yet. Double-click CleanTV Speed on your Desktop to run one.</p>`; return; }
  const last = TESTS[TESTS.length - 1];
  document.getElementById("latest-conn").textContent =
    `Last test ${when(last.t)} on ${label(last)}` + (last.rssi != null ? ` · signal ${last.rssi} dBm` : "") + (last.rate != null ? ` · link ${last.rate} Mbps` : "");
  const shown = TESTS.slice(-30);
  const c = chart(shown);
  const groups = new Map();
  for (let i = TESTS.length - 1; i >= 0; i--) {
    const t = TESTS[i], k = label(t);
    if (!groups.has(k)) groups.set(k, []);
    groups.get(k).push(t);
  }
  const mean = (a, k) => { const v = a.map(t => t[k]).filter(x => x != null); return v.length ? v.reduce((p, q) => p + q, 0) / v.length : null; };
  const hasNames = TESTS.some(t => t.name);
  const connRows = [...groups].map(([k, a]) => {
    const r = mean(a, "rssi");
    return `<tr><td>${esc(k)}</td><td class="n">${a.length}</td><td class="n">${fmt(mean(a, "down"))}</td><td class="n">${fmt(mean(a, "up"))}</td><td class="n">${fmt(mean(a, "ping"))}</td><td class="n">${r == null ? "—" : Math.round(r)}</td>${hasNames ? `<td class="hide-sm">${esc(a[0].name || "")}</td>` : ""}</tr>`;
  }).join("");
  const recentRows = TESTS.slice(-10).reverse().map(t =>
    `<tr><td>${when(t.t)}</td><td>${esc(label(t))}</td><td class="n">${fmt(t.down)}</td><td class="n">${fmt(t.up)}</td><td class="n">${fmt(t.ping)}</td><td class="n">${fmt(t.jitter)}</td><td class="n">${t.rssi ?? "—"}</td></tr>`).join("");
  app.innerHTML = `
    <div class="kpis">
      ${kpi("Download", "var(--down)", summary("down", true), "Mbps")}
      ${kpi("Upload", "var(--up)", summary("up", true), "Mbps")}
      ${kpi("Ping", "var(--ping)", summary("ping", false), "ms")}
      <div class="card kpi"><div class="name">Tests</div><div><span class="value">${TESTS.length}</span></div><div class="detail">since ${day(TESTS[0].t)}</div></div>
    </div>
    <div class="card">
      <div class="chart-head"><h2>Speed over the last tests</h2>
        <div class="legend"><span><i class="dot" style="background:var(--down)"></i>Download</span><span><i class="dot" style="background:var(--up)"></i>Upload</span><span>Mbps</span></div></div>
      ${c.svg}
    </div>
    <div class="stack">
      <div class="card"><h2>Connections</h2><div class="scroll"><table>
        <tr><th>Connection</th><th class="n">Tests</th><th class="n">Down</th><th class="n">Up</th><th class="n">Ping</th><th class="n">Signal</th>${hasNames ? `<th class="hide-sm">Network</th>` : ""}</tr>${connRows}</table></div></div>
      <div class="card"><h2>Recent tests</h2><div class="scroll"><table>
        <tr><th>When</th><th>Connection</th><th class="n">Down</th><th class="n">Up</th><th class="n">Ping</th><th class="n">Jitter</th><th class="n">Signal</th></tr>${recentRows}</table></div>
        <p class="note">Down and Up in Mbps, Ping and Jitter in ms, Signal in dBm. Results are kept on this Mac only.</p></div>
    </div>`;
  // Crosshair and tooltip on the chart.
  const svg = app.querySelector("svg"), tip = document.getElementById("tip");
  const cursor = svg.querySelector("#cursor"), marks = svg.querySelector("#marks");
  svg.querySelector("#hit").addEventListener("mousemove", ev => {
    const pt = svg.createSVGPoint(); pt.x = ev.clientX; pt.y = ev.clientY;
    const p = pt.matrixTransform(svg.getScreenCTM().inverse());
    const i = shown.length < 2 ? 0 : Math.max(0, Math.min(shown.length - 1, Math.round((p.x - c.L) / c.pw * (shown.length - 1))));
    const t = shown[i], cx = c.x(i);
    cursor.setAttribute("x1", cx); cursor.setAttribute("x2", cx); cursor.setAttribute("visibility", "visible");
    marks.innerHTML = [["down", "var(--down)"], ["up", "var(--up)"]].filter(([k]) => t[k] != null)
      .map(([k, col]) => `<circle cx="${cx}" cy="${c.y(t[k])}" r="5" fill="${col}" stroke="var(--surface)" stroke-width="2"/>`).join("");
    tip.innerHTML = `<b>${when(t.t)}</b><span style="color:var(--down)">●</span> Download ${fmt(t.down)} Mbps<br><span style="color:var(--up)">●</span> Upload ${fmt(t.up)} Mbps<br>Ping ${fmt(t.ping)} ms<br>${esc(label(t))}`;
    tip.style.display = "block";
    tip.style.left = Math.min(ev.clientX + 14, innerWidth - tip.offsetWidth - 8) + "px";
    tip.style.top = (ev.clientY + 14) + "px";
  });
  svg.querySelector("#hit").addEventListener("mouseleave", () => {
    tip.style.display = "none"; cursor.setAttribute("visibility", "hidden"); marks.innerHTML = "";
  });
}
render();
</script>
</body>
</html>
#DASHBOARD-END
