(ns eponai.client.parser.mutate
  (:require
    [eponai.common.parser :as parser :refer [client-mutate]]
    [eponai.client.auth :as auth]
    [eponai.common.database :as db]
    [taoensso.timbre :refer [debug warn]]))

;; ################ Remote mutations ####################
;; Remote mutations goes here. We share these mutations
;; with all client platforms (web, ios, android).
;; Local mutations should be defined in:
;;     eponai.<platform>.parser.mutate

(defmethod client-mutate 'session/signout
  [_ _ _]
  {:action (fn []
             #?(:cljs
                (.removeItem js/localStorage "idToken")))})

(defn logged-in-update-cart [state item]
  (let [cart (db/one-with (db/db state) {:where '[[?e :cart/items]]})]
    (db/transact-one state [:db/add cart :cart/items (:db/id item)])))

(defn anon-update-cart [item]
  #?(:cljs
     (if-let [stored-cart (.getItem js/localStorage "cart")]
       (let [cart (cljs.reader/read-string stored-cart)
             new-cart (update cart :cart/items conj (:db/id item))]
         (.setItem js/localStorage "cart" new-cart))
       (.setItem js/localStorage "cart" {:cart/items [(:db/id item)]}))))

(defmethod client-mutate 'shopping-bag/add-item
  [{:keys [target state]} _ {:keys [item]}]
  (if target
    {:remote/user (auth/is-logged-in?)}
    {:action (fn []
               (if (auth/is-logged-in?)
                 (logged-in-update-cart state item)
                 (anon-update-cart item)))}))

(defmethod client-mutate 'search/search
  [{:keys [target]} _ {:keys [search-string]}]
  (if target
    {:remote true}
    {:action (fn []
               (debug "Mutate search string: " search-string))}))

(defmethod client-mutate 'routes/set-route!
  [{:keys [state]} _ {:keys [route route-params]}]
  {:action (fn []
             (debug "Setting route: " route " route-params: " route-params)
             (let [id (:db/id (db/entity (db/db state) [:ui/singleton :ui.singleton/routes]))]
               (db/transact state [[:db/add id :ui.singleton.routes/current-route route]
                                   (if (seq route-params)
                                     [:db/add id :ui.singleton.routes/route-params route-params]
                                     [:db.fn/retractAttribute id :ui.singleton.routes/route-params])])))})

(defmethod client-mutate 'beta/vendor
  [{:keys [target]} _ p]
  (debug "beta/vendor with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'photo/upload
  [{:keys [target]} _ p]
  (debug "photo/upload with params: " p)
  (if target
    {:remote true}))

;; ########### STRIPE ###############

(defmethod client-mutate 'stripe/create-account
  [{:keys [target]} _ p]
  (debug "stripe/create-account with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'stripe/create-product
  [{:keys [target]} _ p]
  (debug "stripe/create-product with params: " p)
  (if target
    {:remote true}))

(defmethod client-mutate 'stripe/delete-product
  [{:keys [target]} _ p]
  (debug "stripe/delete-product with params: " p)
  (if target
    {:remote true}))