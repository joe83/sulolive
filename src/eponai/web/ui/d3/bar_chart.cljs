(ns eponai.web.ui.d3.bar-chart
  (:require
    [cljsjs.d3]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [goog.string :as gstring]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]))

(defui BarChart
  Object
  (make-axis [_ width height domain]
    (let [x-scale (.. js/d3 -scale ordinal
                      (rangeRoundBands #js [0 width] 0.1)
                      (domain (.map domain (fn [d] (.-name d)))))

          y-scale (.. js/d3 -scale linear
                      (range #js [height 0])
                      (nice)
                      (domain #js [0 (.. js/d3
                                         (max domain (fn [d] (.-value d))))]))]
      {:x-axis (.. js/d3 -svg axis
                   (scale x-scale)
                   (orient "bottom")
                   (tickSize (* -1 height) 0 0))

       :y-axis (.. js/d3 -svg axis
                   (scale y-scale)
                   (orient "left")
                   (ticks (max (/ height 50) 2))
                   (tickFormat (.. js/d3
                                   (format ",.2f"))))
       :x-scale x-scale
       :y-scale y-scale}))

  (create [this]
    (let [{:keys [id width height data]} (om/props this)
          svg (d3/build-svg (str "#bar-chart-" id) width height)

          js-domain (clj->js (flatten (map :values data)))
          js-data (clj->js data)

          {:keys [margin]} (om/get-state this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})

          {:keys [x-axis y-axis x-scale y-scale]} (.make-axis this inner-width inner-height js-domain)
          color-scale (.. js/d3 -scale category20
                          (domain (.map js-domain (fn [d] (.-name d)))))
          graph (.. svg
                    (append "g")
                    (attr "class" "bar-chart")
                    (attr "transform" (str "translate(" (:left margin) "," (:top margin) ")")))]
      (.. graph
          (append "g")
          (attr "class" (if (< 30 (.. x-scale rangeBand)) "x axis grid" "x axis grid hidden"))
          (attr "transform" (str "translate(0," inner-height ")"))
          (call x-axis))
      (.. graph
          (append "g")
          (attr "class" "y axis grid")
          (attr "transform" (str "translate(0,0)"))
          (call y-axis))
      (d3/update-on-resize this id)

      (om/update-state! this assoc :svg svg :js-domain js-domain :js-data js-data :x-scale x-scale :y-scale y-scale :x-axis x-axis :y-axis y-axis :color-scale color-scale :graph graph)))

  (update [this]
    (let [{:keys [svg x-scale y-scale x-axis y-axis js-data js-domain margin color-scale graph]} (om/get-state this)
          {:keys [data]} (om/props this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})]

      ; When resize is in progress and sidebar pops in, inner size can be fucked up here.
      ; So just don't do anything in that case, an update will be triggered when sidebar transition is finished anyway.
      (when-not (or (js/isNaN inner-height) (js/isNaN inner-width))
        (if (empty? js-domain)
          (do
            (d3/no-data-insert svg)
            (.. x-scale
                (rangeRoundBands #js [0 inner-width] 0.1)
                (domain #js []))
            (.. y-scale
                (range #js [inner-height 0])
                (domain #js [0 1])))
          (do
            (d3/no-data-remove svg)
            (.. x-scale
                (rangeRoundBands #js [0 inner-width] 0.01)
                (domain (.map js-domain (fn [d] (.-name d)))))
            (.. y-scale
                (range #js [inner-height 0])
                (domain #js [0 (.. js/d3
                                   (max js-domain (fn [d] (.-value d))))]))))

        (.. y-axis
            (ticks (max (/ inner-height 50) 2))
            (tickSize (* -1 inner-width) 0 0))
        (.. x-axis
            (tickSize (* -1 inner-height) 0 0))

        (.. svg
            (selectAll ".x.axis")
            (attr "transform" (str "translate(0, " inner-height ")"))
            (attr "class" (if (< 30 (.. x-scale rangeBand)) "x axis grid" "x axis grid hidden"))
            transition
            (duration 250)
            (call x-axis))

        (.. svg
            (selectAll ".y.axis")
            transition
            (duration 250)
            (call y-axis))

        (mapv
          (fn [data-set]
            (let [data-values (.-values data-set)
                  bars (.. graph
                           (selectAll "rect.bar")
                           (data data-values))
                  texts (.. graph
                            (selectAll "text.bar")
                            (data data-values))]
              (.. bars
                  enter
                  (append "rect")
                  (attr "class" "bar")
                  (attr "transform" (fn [d] (str "translate(" (x-scale (.-name d)) ",0)")))
                  (attr "y" inner-height)
                  (attr "height" 0))

              (.. bars
                  (style "fill" (fn [d] (color-scale (.-name d))))
                  transition
                  (duration 250)
                  (attr "transform" (fn [d] (str "translate(" (x-scale (.-name d)) ",0)")))
                  (attr "width" (.. x-scale rangeBand))
                  (attr "y" (fn [d] (y-scale (.-value d))))
                  (attr "height" (fn [d] (- inner-height (y-scale (.-value d))))))

              (.. bars
                  exit
                  remove)

              (.. texts
                  enter
                  (append "text")
                  (attr "class" "bar")
                  (attr "text-anchor" "middle"))

              (.. texts
                  transition
                  (duration 250)
                  (attr "x" (fn [d] (+ (x-scale (.-name d)) (/ (.. x-scale rangeBand) 2))))
                  (attr "y" (fn [d] (y-scale (.-value d))))
                  (attr "dy" "-0.3em")
                  (text (fn [d] (gstring/format "%.2f" (.-value d)))))

              (.. texts
                  exit
                  remove)

              ()))
          js-data))))

  (initLocalState [_]
    {:margin {:top 20 :bottom 20 :left 0 :right 0}})

  (componentDidMount [this]
    (d3/create-chart this))

  (componentDidUpdate [this _ _]
    (d3/update-chart this))

  (componentWillReceiveProps [this next-props]
    (d3/update-chart-data this next-props))

  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div
         (opts {:id (str "bar-chart-" id)
                :style {:height "100%"
                        :width "100%"}})]))))

(def ->BarChart (om/factory BarChart))