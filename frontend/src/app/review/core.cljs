(ns app.review.core
  (:require
   ["html-entities" :as html-entities]
   [ajax.core :refer [GET POST]]
   [malli.core :as m]
   [app.helpers :refer
    [current-path
     remove-trailing-slash
     fontawesome-icon]]
   [app.editor.core :refer [editor active-file]]
   [app.components.accordion :refer [accordion]]
   [app.components.jumbotron :refer
    [render-error
     loading-screen
     render-succeeded]]
   [app.three-column-layout.core :refer
    [three-column-layout
     instructions-item
     instructions
     status-panel]]
   [app.components.snippets :refer
    [snippets
     add-snippet
     add-snippet-from-backend-map
     on-snippet-textarea-change
     highlight-snippet-in-text]]
   [app.review.logic :refer [index-of-file]]
   [app.review.events :refer
    [on-accordion-item-show
     vote on-vote-button-click
     on-change-form-input]]
   [app.review.atoms :refer
    [files
     error-description
     error-title
     status
     form
     votes]]))

(def InputSchema
  (let [File [:map [:name :string] [:content :string]]]
    [:map {:closed false} ;; TODO Change to true once we get the right data
     ;; TODO Some ID needs to be there and we need to send it back
     ;; [:username [:maybe :string]] ;; TODO We don't want username from backend
     [:fail_reason :string]
     [:how_to_fix :string]
     [:container_file [:maybe File]]
     [:spec_file [:maybe File]]
     [:logs [:map-of :any File]]]))

(defn handle-validated-backend-data [data]
  (reset! form (assoc @form :how-to-fix (:how_to_fix data)))
  (reset! form (assoc @form :fail-reason (:fail_reason data)))

  (reset!
   files
   (vec (map (fn [log]
               ;; We must html encode all HTML characters
               ;; because we are going to render the log
               ;; files dangerously
               (update log :content #(.encode html-entities %)))
             (vals (:logs data)))))

  ;; Parse snippets from backend and store them to @snippets
  (doall (for [file @files
               :let [file-index (index-of-file (:name file))]]
           (doall (for [snippet (:snippets file)]
                    (add-snippet-from-backend-map
                     @files
                     file-index
                     snippet)))))

  ;; Highlight snippets in the log text
  (doall (for [snippet @snippets
               :let [file-index (index-of-file (:file snippet))
                     content (:content (get @files file-index))
                     content (highlight-snippet-in-text content snippet)]]
           (reset! files (assoc-in @files [file-index :content] content)))))

(defn handle-backend-error [title description]
  (reset! status "error")
  (reset! error-title title)
  (reset! error-description description))

(defn handle-validation-error [title description]
  ;; Go back to "has files" state, let users fix
  ;; validation errors
  (reset! status nil)
  (reset! error-title title)
  (reset! error-description description))

(defn init-data-review []
  (GET (str "/frontend" (remove-trailing-slash (current-path)) "/random")
    :response-format :json
    :keywords? true

    :error-handler
    (fn [error]
      (handle-backend-error
       (:error (:response error))
       (:description (:response error))))

    :handler
    (fn [data]
      (if (m/validate InputSchema data)
        (handle-validated-backend-data data)
        (handle-backend-error
         "Invalid data"
         "Got invalid data from the backend. This is likely a bug.")))))

(defn submit-form []
  (let [body {:fail_reason (:fail-reason @form)
              :how_to_fix (:how-to-fix @form)
              :username (if (:fas @form) (str "FAS:" (:fas @form)) nil)
              :snippets @snippets
              :votes @votes}]

    ;; Remember the username, so we can prefill it the next time
    (when (:fas @form)
      (.setItem js/localStorage "fas" (:fas @form)))

    (reset! status "submitting")

    (POST "/frontend/review/"
      {:params body
       :response-format :json
       :format :json
       :keywords? true

       :error-handler
       (fn [error]
         (handle-validation-error
          (:error (:response error))
          (:description (:response error))))

       :hanlder
       (fn [_]
         (reset! status "submitted"))})))

(defn left-column []
  (instructions
   [(instructions-item
     true
     "We fetched a random sample from our collected data set")

    (instructions-item
     (>= (count (dissoc @votes :how-to-fix :fail-reason)) (count @snippets))
     "Go through all snippets and either upvote or downvote them")

    (instructions-item
     (not= (:fail-reason @votes) 0)
     "Is the explanation why did the build fail correct?")

    (instructions-item
     (not= (:how-to-fix @votes) 0)
     "Is the explanation how to fix the issue correct?")

    (instructions-item nil "Submit")]))

(defn middle-column []
  (editor @files))

(defn buttons [name]
  (let [key (keyword name)]
    [[:button {:type "button"
               :class ["btn btn-vote" (if (> (key @votes) 0)
                                        "btn-primary"
                                        "btn-outline-primary")]
               :on-click #(on-vote-button-click key 1)}
      (fontawesome-icon "fa-thumbs-up")]
     [:button {:type "button"
               :class ["btn btn-vote" (if (< (key @votes) 0)
                                        "btn-danger"
                                        "btn-outline-danger")]
               :on-click #(on-vote-button-click key -1)}
      (fontawesome-icon "fa-thumbs-down")]]))

(defn snippet [text index]
  (let [name (str "snippet-" index)]
    {:title "Snippet"
     :body
     [:textarea
      {:class "form-control"
       :rows "3"
       :placeholder "What makes this snippet relevant?"
       :value text
       :on-change #(do (on-snippet-textarea-change %)
                       (vote (keyword name) 1))}]
     :buttons (buttons name)}))

(defn card [title text name placeholder]
  [:div {:class "card review-card"}
   [:div {:class "card-body"}
    [:h6 {:class "card-title"} title]
    [:textarea {:class "form-control" :rows 3
                :value text
                :placeholder placeholder
                :name name
                :on-change #(do (on-change-form-input %)
                                (vote (keyword name) 1))}]
    [:div {:class "btn-group"}
     (into [:<>] (buttons name))]]])

(defn right-column []
  [:<>
   [:label {:class "form-label"} "Your FAS username:"]
   [:input {:type "text"
            :class "form-control"
            :placeholder "Optional - Your FAS username"
            :value (or (:fas @form) (.getItem js/localStorage "fas"))
            :name "fas"
            :on-change #(on-change-form-input %)}]

   [:label {:class "form-label"} "Interesting snippets:"]
   (when (not-empty @snippets)
     [:div {}
      [:button {:class "btn btn-secondary btn-lg"
                :on-click #(add-snippet files active-file)} "Add"]
      [:br]
      [:br]])

   (accordion
    "accordionItems"
    (vec (map-indexed (fn [i x] (snippet (:comment x) i)) @snippets)))

   (when (empty? @snippets)
     [:div {:class "card" :id "no-snippets"}
      [:div {:class "card-body"}
       [:h5 {:class "card-title"} "No snippets yet"]
       [:p {:class "card-text"}
        (str "Please select interesting parts of the log files and press the "
             "'Add' button to annotate them")]
       [:button {:class "btn btn-secondary btn-lg"
                 :on-click #(add-snippet files active-file)}
        "Add"]]])

   [:br]
   (card
    "Why did the build fail?"
    (:fail-reason @form)
    "fail-reason"
    "Please describe what caused the build to fail.")

   [:br]
   (card
    "How to fix the issue?"
    (:how-to-fix @form)
    "how-to-fix"
    (str "Please describe how to fix the issue in "
         "order for the build to succeed."))

   [:div {}
    [:label {:class "form-label"} "Ready to submit the results?"]
    [:br]
    [:button {:type "submit"
              :class "btn btn-primary btn-lg"
              :on-click #(submit-form)}
     "Submit"]]])

(defn review []
  ;; The js/document is too general, ideally we would like to limit this
  ;; only to #accordionItems but it doesn't exist soon enough
  (.addEventListener js/document "show.bs.collapse" on-accordion-item-show)

  (cond
    (= @status "error")
    (render-error @error-title @error-description)

    (= @status "submitted")
    (render-succeeded)

    @files
    (three-column-layout
     (left-column)
     (middle-column)
     (right-column)
     (status-panel @status @error-title @error-description))

    :else
    (loading-screen "Please wait, fetching logs from our dataset.")))
