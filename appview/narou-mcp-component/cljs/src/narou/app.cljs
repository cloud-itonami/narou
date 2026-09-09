(ns narou.app
  "narou-mcp-component appview — reagent + re-frame, view built from
  jp-go-dds (デジタル庁デザインシステム) hiccup.

  Faithful port of the previous SvelteKit scaffold's status page
  (`svelte/src/routes/+page.svelte`, 62 lines): a static display of this
  Worker's own declared surface — title / project / kind, route count +
  list, wrangler var keys, an XRPC-enabled flag, and its own source path.
  Every field below mirrors the constant `app` object `+page.svelte` held
  in its <script> block; nothing here is invented and nothing is
  simplified away — including the routeCount 0 / routes [] / vars []
  mismatch against this repo's wrangler.jsonc (which does declare two
  `routes` entries and eight `vars` keys): that mismatch pre-existed in
  the Svelte page and is ported as-is, not corrected, because this is a
  frontend migration, not a data audit.

  Two fields are updated, not simplified, to stay honest about what this
  migration itself changed:

  - `:app/relative-path` now names this file, not the deleted Svelte one.
  - `:app/xrpc?` stays `true` (the value the Svelte page held), but the
    fact on the ground changed *underneath* that value, in the opposite
    direction from a naive read: before this migration, `wrangler.jsonc`
    pointed `main` at the SvelteKit worker build output
    (`svelte/.svelte-kit/cloudflare/_worker.js`), which is what actually
    served `POST /xrpc/[...path]` (`svelte/src/routes/xrpc/[...path]/+server.ts`,
    a generic proxy to the AgentGateway MCP router). This migration
    deletes that build output along with the rest of `svelte/`, and — per
    the accompanying wrangler.jsonc change — deletes the `main` key
    entirely rather than pointing it at `src/app.ts` (`src/app.ts` does
    not call `env.ASSETS.fetch()`, so aiming `main` at it would put a
    Worker in front of the static assets and nothing would serve them).
    With no `main`, this Worker config now serves *only* static assets:
    no XRPC route runs through it at all after this change.
    `src/app.ts` does implement its own, different, XRPC surface
    (`/xrpc/com.etzhayyim.narou.*` → `DISPATCHER_URL`, 11 narou-specific
    methods) but — same as before this migration — it is not named by
    `main`, so it was not reachable through this Worker either before or
    after. The original SvelteKit handler is preserved byte-for-byte
    (moved, not deleted) at `../src/xrpc-proxy.ts` but is explicitly NOT
    wired into `src/app.ts` (see that file's own header comment) —
    reviving XRPC reachability, and deciding which of the two proxy
    implementations should own it, is a backend/architecture decision
    out of scope for this frontend migration, not something this
    namespace decides by quietly flipping a boolean.

  `public/index.html`'s inlined <style> was produced once, at authoring
  time, by `jp-go-dds.page/->page` running on the JVM (via this deps.edn's
  jp-go-dds git/sha), concatenating the vendored `dds.css` with
  `jp-go-dds.core/ext-css` — exactly what `jp-go-dds.page/page` composes
  for its own <style> block. This namespace only requires
  `jp-go-dds.core` — the browser bundle does not need `jp-go-dds.page` or
  `html.core` at runtime; those are JVM-only tools used to author the
  static shell once. Regenerate that shell (e.g. if jp-go-dds's core
  components or ext-rules change) with:

    (require '[jp-go-dds.page :as page] '[clojure.java.io :as io])
    (spit \"public/index.html\"
          (page/->page {:title \"narou-mcp-component\"
                         :lang \"ja\"
                         :description \"narou-mcp-component — narou.etzhayyim.com thin edge facade appview (reagent + re-frame + jp-go-dds).\"
                         :css (slurp (io/resource \"jp_go_dds/dds.css\"))}
                        [:div {:id \"app\"} \"narou-mcp-component loading…\"]
                        [:noscript \"narou-mcp-component requires JavaScript.\"]
                        [:script {:src \"js/app.js\"}]))"
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [jp-go-dds.core :as dds]))

;; -- db ------------------------------------------------------------------
;;
;; Same nine facts + own source path that `+page.svelte`'s `app` const
;; held (title/project/name/kind/routeCount/routes/vars/xrpc/relativePath).

(def default-db
  {:app/title "Narou Mcp Component"
   :app/project "etzhayyim-project-narou"
   :app/name "narou-mcp-component"
   :app/kind "appview"
   :app/route-count 0
   :app/routes []
   :app/vars []
   :app/xrpc? true
   :app/relative-path "appview/narou-mcp-component/cljs/src/narou/app.cljs"})

(rf/reg-event-db
 :initialize-db
 (fn [_ _] default-db))

(rf/reg-sub :app/title (fn [db _] (:app/title db)))
(rf/reg-sub :app/project (fn [db _] (:app/project db)))
(rf/reg-sub :app/name (fn [db _] (:app/name db)))
(rf/reg-sub :app/kind (fn [db _] (:app/kind db)))
(rf/reg-sub :app/route-count (fn [db _] (:app/route-count db)))
(rf/reg-sub :app/routes (fn [db _] (:app/routes db)))
(rf/reg-sub :app/vars (fn [db _] (:app/vars db)))
(rf/reg-sub :app/xrpc? (fn [db _] (:app/xrpc? db)))
(rf/reg-sub :app/relative-path (fn [db _] (:app/relative-path db)))

;; -- view ------------------------------------------------------------------

(defn app-view []
  (let [title         @(rf/subscribe [:app/title])
        name          @(rf/subscribe [:app/name])
        kind          @(rf/subscribe [:app/kind])
        project       @(rf/subscribe [:app/project])
        route-count   @(rf/subscribe [:app/route-count])
        routes        @(rf/subscribe [:app/routes])
        vars          @(rf/subscribe [:app/vars])
        xrpc?         @(rf/subscribe [:app/xrpc?])
        relative-path @(rf/subscribe [:app/relative-path])]
    (dds/container

     [:section {:class "dds-ext-section"}
      [:p {:class "dds-ext-lead"} (str "Cloudflare " kind)]
      (dds/heading 1 title)
      [:span {:class "dads-u-mono-16N-150"} name]]

     [:section {:class "dds-ext-section"}
      (dds/grid {:min "12rem"}
        (dds/card [:p {:class "dds-ext-lead"} "Project"] [:strong project])
        (dds/card [:p {:class "dds-ext-lead"} "Routes"] [:strong (str route-count)])
        (dds/card [:p {:class "dds-ext-lead"} "XRPC"]
                  [:strong (if xrpc? "enabled" "not configured")]))]

     [:section {:class "dds-ext-section"}
      (dds/heading 2 "Public Routes" {:size "24"})
      (if (seq routes)
        (dds/card
         (into [:ul {:class "dds-ext-stack"}]
               (map (fn [r] [:li {:class "dads-u-mono-16N-150"} r]) routes)))
        [:p {:class "dds-ext-lead"} "No public route is declared next to this app surface."])]

     [:section {:class "dds-ext-section"}
      (dds/heading 2 "Runtime Bindings" {:size "24"})
      (if (seq vars)
        (into [:div {:class "dds-ext-row"}]
              (map (fn [v] (dds/chip-label v {:color "blue"})) vars))
        [:p {:class "dds-ext-lead"} "No public vars are declared in the nearest wrangler config."])]

     [:section {:class "dds-ext-section"}
      (dds/heading 2 "Source" {:size "24"})
      [:p {:class "dads-u-mono-16N-150"} relative-path]])))

;; -- mount -------------------------------------------------------------------

(defn render []
  (rdom/render [app-view] (.getElementById js/document "app")))

(defn ^:export main []
  (rf/dispatch-sync [:initialize-db])
  (render))
