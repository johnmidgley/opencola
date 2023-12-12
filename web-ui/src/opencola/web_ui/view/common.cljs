(ns opencola.web-ui.view.common 
  (:require [clojure.string :as string] 
            [goog.string :as gstring]
            [markdown-to-hiccup.core :as md2hic]
            [reagent.core :as reagent]
            [opencola.web-ui.location :as location] 
            [reagent.core :as r]))

(defn copy-to-clipboard [text]
  (-> js/navigator .-clipboard (.writeText text)))

(defn swap-atom-data! [event item! key]
  (swap! item! assoc-in [key] (-> event .-target .-value)))
(defn error-control [e!]
  (when @e!
    [:div.error [:p @e!]]))

(defn item-key []
  (let [new-key (random-uuid)]
    new-key))

(def inline-divider [:span.divider " | "])
(defn keyed-divider [] ^{:key (item-key)} [:span.divider " | "])
(def image-divider [:img.divider {:src "../img/divider.png"}])
(def nbsp (gstring/unescapeEntities "&nbsp;"))

(defn icon [class icon-class]
  [:span {:class (str class " " icon-class)}])

(defn empty-page-instructions [page problem-text]
  [:div.content-list
   [:div.list-item.instruction-item
    [:div.instructions-wrapper
     [:img.nola-img {:src "img/nola.png"}]
     [:span.item-name.title "Snap! "problem-text]
     (when (= page :peers)
       [:div.instructions
        [:span.instruction "Add peers by clicking the add peer icon (" [icon "icon" "icon-new-peer"] ") on the top right!"]])
     (when (= page :feed)
       [:div.instructions
        [:span.instruction "Add posts by clicking the add post icon (" [icon "icon" "icon-new-post"] ") on the top right!"]
        [:span.instruction "Add peers by clicking the peers icon (" [icon "icon" "icon-peers"] ") on the top right!"]])
     [:span.instruction "Or browse the help page by clicking the menu icon ("
      [icon "icon" "icon-menu"]
      ") on the top right and clicking on the help button ("
      [icon "icon" "icon-help"]
      ")"]]]])

(defn button-component [config on-click!]
  (let [{src :src
         text :text
         icon-class :icon-class
         disabled? :disabled
         name :name
         class :class} config]
    [:button.button-component {:type "button"
                               :on-click on-click!
                               :name name
                               :disabled (when (not (nil? disabled?)) disabled?)
                               :class (when class class)}
     (when icon-class
       [icon "button-icon" icon-class])
     (when src
       [:img.button-img {:src src}])
     (when text
       [:span.button-text text])]))

(defn text-input [text on-change]
  (let [edit-text! (atom text)]
    [:input.text-input
     {:type "text"
      :value @edit-text!
      :on-change (fn [e]
                   (let [val (-> e .-target .-value)]
                     (reset! edit-text! val)
                     (on-change val)))}]))

(defn input-text [item! key editing?]
  [:input.reset.input-text
   {:type "text"
    :disabled (not editing?)
    :value (key @item!)
    :on-change #(swap! item! assoc-in [key] (-> % .-target .-value))}])

(defn text-input-component [config on-change]
  (let [{value :value
         placeholder :placeholder
         on-enter :on-enter
         disabled? :disabled
         icon-class :icon-class
         title :title
         class :class
         name :name
         copy-button :copy-button} config]
    [:div.text-input-wrapper {:class (when class class)}
     (when title [:span.text-input-title title])
     (when icon-class [icon "icon" icon-class])
     [:input.reset.text-input-component
      {:type "text"
       :placeholder (when placeholder placeholder)
       :disabled disabled?
       :name name
       :value value
       :on-change on-change
       :on-keyUp (when on-enter 
                   #(when (= (.-key %) "Enter") on-enter))}]
     (when copy-button 
       [button-component {:icon-class "icon-copy" :class "action-button"} #(copy-to-clipboard value)])]))

(defn input-checkbox [config on-change]
  (let [{checked? :checked
         disabled? :disabled
         title :title
         icon-class :icon-class
         name :name
         class :class} config] 
    [:div.input-checkbox-wrapper {:class (when class class)}
     (when title 
       [:span.input-checkbox-title title])
     (when icon-class [icon "icon" icon-class])
     [:input.input-checkbox
      {:type "checkbox"
       :disabled disabled?
       :name name
       :checked checked?
       :on-change on-change}]]))

;; https://github.com/reagent-project/reagent/blob/master/doc/CreatingReagentComponents.md
(defn simple-mde [id placeholder text state!] 
  (fn [] 
    (reagent/create-class           
     {:display-name  "my-component"
      
      :component-did-mount         
      (fn [_] 
        ;; super ugly. Since SimpleMDE is a non-react, js component, we need some way to be able to 
        ;; get the text out. We do this by storing the object in an atom that can be accessed outside
        ;; of the react control. Might be better to just put a proxy object in the state. Not sure
        ;; if there's a cleaner way to do this. 
        (reset! state! (js/SimpleMDE. 
                        (clj->js 
                         {:element (js/document.getElementById id)
                          :forceSync true
                          :placeholder placeholder
                          :autofocus true
                          :spellChecker false
                          :status false
                          }))))
      
      :reagent-render 
      (fn []
        [:textarea {:id id :value text :on-change #()}])})))

(defn md->component [attributes md-text]
  (let [hiccup (->> md-text (md2hic/md->hiccup) (md2hic/component))]
    (assoc hiccup 1 attributes)))

(defn hidden-file-input [id on-change]
  [:input {:type "file"
           :id id
           :multiple true
           :style {:display "none"}
           :on-change #(on-change (.. % -target -files))}])

(defn select-files-control [content on-change]
  (let [input-id (str (random-uuid))]
    [:span.button.action-button {:on-click #(.click (js/document.getElementById input-id))}
     [:input {:type "file"
              :id input-id
              :multiple true
              :style {:display "none"}
              :on-change #(on-change (.. % -target -files))}]
     content]))

(defn extenstion [filename]
  (let [parts (string/split filename #"\.")]
    (last parts)))

(defn image? [filename]
  (contains? #{"jpg" "jpeg" "png" "gif" "svg"} (extenstion filename)))

(defn text-area [text on-change]
  (let [edit-text! (atom text)]
    [:textarea.text-area
     {:type "text"
      :value @edit-text!
      :on-change (fn [e]
                   (let [val (-> e .-target .-value)]
                     (reset! edit-text! val)
                     (on-change val)))}]))

(defn progress-bar [visible?! progress!]
  [:progress {:class "attach-progress" :hidden (not @visible?!) :value @progress! :max 100}])

(defn upload-progress [visible?! progress!]
  (when @visible?!
  [:span
   "Uploading: "
   [progress-bar visible?! progress!]
   (when (= @progress! 100) " processing...")]))

(defn popout-menu [config menu-open?! on-click! menu-content]
  (let [{class :class} config]
    (when @menu-open?!
      [:div.popout-menu-content {:on-click on-click! :class class}
       menu-content])))

(defn select-menu-content [item-collection name-key id-key selected-item! on-click!]
  [:div.select-menu-content 
   (doall (for [item item-collection]
            (when (not= item @selected-item!)
              ^{:key (id-key item)} [button-component
                                     {:class "select-button" :text (name-key item)}
                                     #(do (on-click! (id-key item)) (reset! selected-item! item))])))])

(defn select-menu [config item-collection current-item name-key id-key on-select!]
  (let [{class :class
         popout-class :popout-class} config
        menu-open?! (r/atom false) 
        selected-item! (r/atom (first (filter #(= (id-key %) (id-key current-item)) item-collection)))
        content [select-menu-content item-collection name-key id-key selected-item! on-select!]]
    (fn []
      [:div.select-menu-wrapper {:class class}
       [:div.select-menu-toggle {:on-click #(swap! menu-open?! not) :aria-expanded @menu-open?!}
        [:span.current-item (name-key @selected-item!)]
        [icon "icon" (if @menu-open?! "icon-hide" "icon-show")]]
       [popout-menu
        {:class popout-class}
        menu-open?!
        #(swap! menu-open?! not)
        content]])))


(defn string-to-range [s range-max]
  (mod (hash s) range-max))

(defn create-hsl [s saturation lightness] 
  (str "hsl("
       (str (string-to-range s 360)) "," 
       saturation "%,"
       lightness "%)"))

(defn initials [s]
  (->> (string/split s #"\s+") (map first) (take 2) (apply str)))

(defn profile-img [image-uri name on-click!]
  (let [name (string/replace name #"\)|You \(" "")] 
    [:div.profile-img-wrapper {:on-click on-click!}
     (if (seq image-uri)
       [:img.profile-img {:src image-uri :alt name}]
       [:span.generated-img {:style {:background-color (create-hsl name 65 75)}} (string/upper-case (initials name))])]))

(defn edit-control-buttons [config deletable? error!]
  (let [{on-save :on-save
         on-cancel :on-cancel
         on-delete :on-delete
         save-text :save-text
         cancel-text :cancel-text
         delete-text :delete-text
         save-disabled? :save-disabled
         class :class} config]
    [:div.edit-control-buttons {:class (when class class)}
     [button-component {:class "edit-control-button"
                        :name "save-button"
                        :text (if save-text save-text "Save") 
                        :disabled (when (not (nil? save-disabled?)) save-disabled?)} 
      on-save]
     [button-component {:class "edit-control-button" 
                        :name "cancel-button"
                        :text (if cancel-text cancel-text "Cancel")} 
      on-cancel]
     (when deletable?
      [button-component {:class "edit-control-button delete-button caution-color"
                         :name "delete-button"
                         :text (if delete-text delete-text "Delete")} 
       on-delete])
     (error-control error!)]))