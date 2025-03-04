(ns frontend.handler.route
  (:require [frontend.util :as util]
            [reitit.frontend.easy :as rfe]
            [reitit.frontend.history :as rfh]
            [frontend.state :as state]
            [goog.dom :as gdom]
            [frontend.handler.ui :as ui-handler]
            [frontend.db :as db]
            [frontend.date :as date]
            [clojure.string :as string]
            [medley.core :as medley]
            [frontend.text :as text]))

(defn redirect!
  "If `push` is truthy, previous page will be left in history."
  [{:keys [to path-params query-params push]
    :or {push true}}]
  (let [route-fn (if push rfe/push-state rfe/replace-state)]
    (state/save-scroll-position! (util/scroll-top))
    (route-fn to path-params query-params)))

(defn redirect-to-home!
  []
  (redirect! {:to :home}))

(defn get-title
  [name path-params]
  (case name
    :home
    "Logseq"
    :repos
    "Repos"
    :repo-add
    "Add another repo"
    :graph
    "Graph"
    :all-files
    "All files"
    :all-pages
    "All pages"
    :all-journals
    "All journals"
    :file
    (str "File " (:path path-params))
    :new-page
    "Create a new page"
    :page
    (let [name (:name path-params)
          block? (util/uuid-string? name)]
      (if block?
        (if-let [block (db/entity [:block/uuid (medley/uuid name)])]
          (let [content (text/remove-level-spaces (:block/content block)
                                                  (:block/format block))]
            (if (> (count content) 48)
              (str (subs content 0 48) "...")
              content))
          "Page no longer exists!!")
        (let [page (db/pull [:block/name (string/lower-case name)])]
          (or (:block/original-name page)
              (:block/name page)
              "Logseq"))))
    :tag
    (str "#"  (:name path-params))
    :diff
    "Git diff"
    :draw
    "Draw"
    :settings
    "Settings"
    :import
    "Import data into Logseq"
    "Logseq"))

(defn update-page-title!
  [route]
  (let [{:keys [data path-params]} route
        title (get-title (:name data) path-params)]
    (util/set-title! title)))

(defn jump-to-anchor!
  [anchor-text]
  (when anchor-text
    (js/setTimeout #(ui-handler/highlight-element! anchor-text) 200)))

(defn set-route-match!
  [route]
  (let [route route]
    (swap! state/state assoc :route-match route)
    (update-page-title! route)
    (if-let [anchor (get-in route [:query-params :anchor])]
      (jump-to-anchor! anchor)
      (util/scroll-to (util/app-scroll-container-node)
                      (state/get-saved-scroll-position)
                      false))))

(defn go-to-search!
  [search-mode]
  (when search-mode
    (state/set-search-mode! search-mode))
  (when-let [element (gdom/getElement "search-field")]
    (.focus element)))

(defn go-to-journals!
  []
  (state/set-journals-length! 2)
  (let [route (if (state/custom-home-page?)
                :all-journals
                :home)]
    (redirect! {:to route}))
  (util/scroll-to-top))

(defn- redirect-to-file!
  [page]
  (when-let [path (-> (db/get-page-file (string/lower-case page))
                      :db/id
                      (db/entity)
                      :file/path)]
    (redirect! {:to :file
                :path-params {:path path}})))

(defn toggle-between-page-and-file!
  [_e]
  (let [current-route (state/get-current-route)]
    (case current-route
      :home
      (redirect-to-file! (date/today))

      :all-journals
      (redirect-to-file! (date/today))

      :page
      (when-let [page-name (get-in (state/get-route-match) [:path-params :name])]
        (redirect-to-file! page-name))

      :file
      (when-let [path (get-in (state/get-route-match) [:path-params :path])]
        (when-let [page (db/get-file-page path)]
          (redirect! {:to :page
                      :path-params {:name page}})))

      nil)))
