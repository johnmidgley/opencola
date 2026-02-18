; Copyright 2024-2026 OpenCola
; 
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
; 
;    http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns opencola.web-ui.view.common 
  (:require [clojure.string :as string] 
            [goog.string :as gstring]
            [markdown-to-hiccup.core :as md2hic] 
            [reagent.core :as r]))

(defn copy-to-clipboard [text]
  (let [e (js/document.createElement "textarea")]
    (set! (.-value e) text)
    (.appendChild js/document.body e)
    (.select e)
    (js/document.execCommand "copy")
    (.removeChild js/document.body e)))

(defn swap-atom-data! [event item! key]
  (swap! item! assoc-in [key] (-> event .-target .-value)))

(defn error-control [e!]
  (when @e!
    (println @e!)
    [:div.error [:p @e!]]))

(defn item-key []
  (let [new-key (random-uuid)]
    new-key))

(defn tool-tip [config]
  (let [{text :text
         tip-position :tip-position
         class :class} config]
    [:tool-tip
     {:class (str "tool-tip " class)
      :role "tooltip" 
      :data-tip-position (or tip-position "tip-top")} 
     text]))

(defn keyed-divider [] ^{:key (item-key)} [:span.divider " | "])
(def nbsp (gstring/unescapeEntities "&nbsp;"))

(defn icon [config]
  (let [{class :class
         icon-class :icon-class
         wrapper-class :wrapper-class
         tool-tip-text :tool-tip-text
         tool-tip-class :tool-tip-class} config]
    [:div.icon-wrapper {:class wrapper-class}
     [:span.icon {:class (str (when class (str class " ")) icon-class)}]
     (when tool-tip-text [tool-tip {:text tool-tip-text :class tool-tip-class}])]))

(defn empty-page-instructions [page problem-text]
  [:div.content-list
   [:div.list-item.instruction-item
    [:div.instructions-wrapper
     [:img.nola-img {:src "img/nola.png"}]
     [:span.item-name.title "Snap! "problem-text]
     (when (= page :peers)
       [:div.instructions
        [:span.instruction "Click " [icon {:icon-class "icon-new-peer" :class "example-img"}] " to add new peers."]])
     (when (= page :feed)
       [:div.instructions
        [:span.instruction "Click " [icon {:icon-class "icon-new-post" :class "example-img"}] " to add a post."]
        [:span.instruction "Click " [icon {:icon-class "icon-peers" :class "example-img"}] " to open the peers page."]])
     [:span.instruction "Click "[icon {:icon-class "icon-menu" :class "example-img"}] " then "
      [icon {:icon-class "icon-help" :class "example-img"}]
      " to browse the help page!"]]]])

(defn button-component [config on-click!]
  (let [{src :src
         text :text
         icon-class :icon-class
         disabled? :disabled
         name :name
         class :class
         tool-tip-text :tool-tip-text
         tip-position :tip-position} config]
    [:button.button-component {:type "button"
                               :on-click on-click!
                               :name name
                               :disabled (when (not (nil? disabled?)) disabled?)
                               :class class
                               }
     (when tool-tip-text
       [tool-tip {:text tool-tip-text :tip-position tip-position}])
     (when icon-class
       [icon {:class "button-icon" :icon-class icon-class}])
     (when src
       [:img.button-img {:src src}])
     (when text
       [:span.button-text text])]))

(defn copy-button-component [config copy-text!]
  (let [{class :class} config
        is-active (r/atom false)]
    (fn []
      [button-component {
                         :icon-class "icon-copy" 
                         :class (str "action-button " class (when @is-active " copy-active"))} 
       (fn [] (js/setTimeout #(reset! is-active false) 1000) (reset! is-active true) (copy-to-clipboard @copy-text!))])))

(defn text-input-component
  ([value! config on-change] 
   (let [{placeholder :placeholder
          on-key-up :on-key-up
          disabled? :disabled
          icon-class :icon-class
          icon-tool-tip-text :icon-tool-tip-text
          help-text :help-text
          title :title
          class :class
          name :name
          copy-button-class :copy-button-class} config]
     [:div.text-input-wrapper {:class class}
      (when title [:span.text-input-title title])
      (when icon-class [icon {:icon-class icon-class :tool-tip-text icon-tool-tip-text}])
      [:input.reset.text-input-component
       {:type "text"
        :placeholder placeholder
        :disabled disabled?
        :name name
        :value @value!
        :on-change on-change
        :on-keyUp on-key-up}]
      (when copy-button-class
        [copy-button-component
         {:class (str "copy-button " copy-button-class)}
         value!])
      (when help-text
        [icon {:icon-class "icon-help" :wrapper-class "help-indicator" :tool-tip-text help-text}])]))
  ;; TODO: find a better way to handle static text use cases
  ([config on-change] 
   [text-input-component (r/atom (:value config)) config on-change]))


(defn input-checkbox [config on-change]
  (let [{checked? :checked
         disabled? :disabled
         title :title
         icon-class :icon-class
         name :name
         class :class
         tool-tip-text :tool-tip-text} config] 
    [:div.input-checkbox-wrapper {:class class}
     (when title 
       [:span.input-checkbox-title title])
     (when icon-class [icon {:icon-class icon-class :tool-tip-text tool-tip-text}]) 
     [:input.input-checkbox
      {:type "checkbox"
       :disabled disabled?
       :name name
       :checked checked?
       :on-change on-change}]]))

(defn collapsable-list [items key-fn preview-count expand-prompt collapse-prompt expanded? config]
  (let [expanded?! (r/atom expanded?)
        preview-items (take preview-count items)
        more (- (count items) preview-count)]
    [:div.collapsable-list {:class (:class config)}
     [:div.list-content 
      (doall (for [item (if expanded?! items preview-items)]
               ^{:key (key-fn item)} item))]
     (when (> more 0)
       [button-component {:class "expand-button "
                          :text (if expanded?! expand-prompt collapse-prompt)
                          :icon-class (if expanded?! "icon-show" "icon-hide")}
        (fn [] (swap! expanded?! #(not %)))])]))

;; https://github.com/reagent-project/reagent/blob/master/doc/CreatingReagentComponents.md
(defn simple-mde [id placeholder text state!] 
  (fn [] 
    (r/create-class           
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
     [tool-tip {:text "Attach"}]
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

(defn select-menu [config item-collection current-item-id-key! id-key name-key on-select] 
  (let [{class :class
         popout-class :popout-class} config
        menu-open?! (r/atom false)
        menu-hovered?! (r/atom false)] 
    (fn []
      (let [selected-item (or (first (filter #(= (id-key %) @current-item-id-key!) item-collection)) (first item-collection))]
       [:div.select-menu-wrapper {:class class
                                  :on-mouse-enter #(reset! menu-hovered?! true)
                                  :on-mouse-leave #(reset! menu-hovered?! false)}
        [:button.select-menu-toggle.button {
                                            :on-click #(swap! menu-open?! not)
                                            :on-blur #(when (not @menu-hovered?!) (reset! menu-open?! false)) 
                                            :aria-expanded @menu-open?!}
         [:span.current-item (name-key selected-item)]
         [icon {:icon-class (if @menu-open?! "icon-hide" "icon-show")}]]
        [popout-menu
         {:class popout-class}
         menu-open?!
         #(reset! menu-open?! false)
         [:div.select-menu-content
          (doall (for [item item-collection]
                   (when (not= item selected-item)
                     ^{:key (or (id-key item) "")} [button-component
                                            {:class "select-button" :text (name-key item)}
                                            #(do (on-select (id-key item)) (reset! current-item-id-key! (id-key item)))])))]]]))))


(defn string-to-range [s range-max]
  (mod (hash s) range-max))

(defn create-hsl [s saturation lightness] 
  (str "hsl("
       (str (string-to-range s 360)) "," 
       saturation "%,"
       lightness "%)"))

(defn initials [s]
  (->> (string/split s #"\s+") (map first) (take 2) (apply str)))

(defn profile-img [image-uri name id on-click!]
  (let [name (if name (string/replace name #"\)|You \(" "") name)
        img? (some? (seq image-uri))]
    [:div.profile-img-wrapper {:on-click on-click! :data-img-present img?}
     (if img?
       [:img.profile-img {:src image-uri :alt name}]
       (if (not= name "")
         [:span.generated-img {:style {:background-color (when id (create-hsl id 65 75)) :color (when (not id) "var(--text-color)")}} (string/upper-case (initials name))]
         [icon {:icon-class "icon-persona"}]))]))

(defn edit-control-buttons [config error!]
  (let [{on-save :on-save
         on-cancel :on-cancel
         on-delete :on-delete
         save-text :save-text
         cancel-text :cancel-text
         delete-text :delete-text
         save-disabled? :save-disabled
         class :class} config]
    [:div.edit-control-buttons {:class class}
     [button-component {:class "edit-control-button"
                        :name "save-button"
                        :text (if save-text save-text "Save") 
                        :disabled (when (not (nil? save-disabled?)) save-disabled?)} 
      on-save]
     [button-component {:class "edit-control-button" 
                        :name "cancel-button"
                        :text (if cancel-text cancel-text "Cancel")} 
      on-cancel]
     (when on-delete 
      [button-component {:class "edit-control-button delete-button caution-color"
                         :name "delete-button"
                         :text (if delete-text delete-text "Delete")} 
       on-delete])
     (error-control error!)]))
