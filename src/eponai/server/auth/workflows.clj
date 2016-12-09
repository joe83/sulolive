(ns eponai.server.auth.workflows
  (:require
    [cemerick.friend :as friend]
    [cemerick.friend.workflows :as workflows]
    [eponai.server.auth.auth0 :as auth0]
    [cemerick.url :as url]
    [clojure.core.async :refer [go]]
    [eponai.server.external.facebook :as fb]
    [eponai.server.external.stripe :as stripe]
    [ring.util.response :as r]
    [ring.util.request :refer [path-info request-url]]
    [taoensso.timbre :refer [debug error info]])
  (:import (clojure.lang ExceptionInfo)))

;(defn redirect-login-failed [message]
;  (r/status (r/response {:status  :login-failed
;                         :message (or message "Oops, something when wrong when creating your account. Try again later.")}) 500))

;(defn redirect-verify-email []
;  (r/status (r/response {:status :verification-needed
;                         :message "Check your inbox for a verification email!"}) 500))

;(defn unauthenticated []
;  (fn [req]
;    (let [auth (friend/current-authentication req)]
;      (debug "Workflow unauthenticated: " auth))
;    (r/redirect "/signup")))

(defn unauthorized [realm]
  (fn [& stuff]
    (debug "UNaothrized request: " stuff)
    {:status  401
     :headers {"Content-Type"     "text/plain"}}))

(defn simple []
  (fn [{:keys [::friend/auth-config] :as request}]
    ;(when-not (some? (friend/current-authentication request)))
    (let [{:keys [credential-fn]} auth-config]
      ((workflows/http-basic :credential-fn (partial credential-fn :simple) :realm "Friend demo") request))))

(defn unauthenticated []
  (fn [request]
    (workflows/http-basic-deny "Friend demo" request)))


(defn simple-login [realm]
  (fn [request]
    (let [{:strs [authorization]} (:headers request)])))

;(defn login-authenticate
;  "Check request username and password against authdata
;  username and passwords.
;  On successful authentication, set appropriate user
;  into the session and redirect to the value of
;  (:next (:query-params request)). On failed
;  authentication, renders the login page."
;  [request]
;  (let [username (get-in request [:form-params "username"])
;        password (get-in request [:form-params "password"])
;        session (:session request)
;        found-password (get authdata (keyword username))]
;    (if (and found-password (= found-password password))
;      (let [next-url (get-in request [:query-params :next] "/")
;            updated-session (assoc session :identity (keyword username))]
;        (-> (redirect next-url)
;            (assoc :session updated-session)))
;      (let [content (slurp (io/resource "login.html"))]
;        (render content request)))))

;(defn simple []
;  (fn [{:keys [params]}]
;    (let [{:keys [psswd]} params]
;      (when (= psswd "hejsan")
;        (workflows/make-auth {:identity "admin" :roles :admin})))))

;(defn http-basic-deny [realm & stuff]
;  (debug "UNaothrized request: " stuff)
;  {:status  401
;   :headers {"Content-Type"     "text/plain"
;             "WWW-Authenticate" (format "Basic realm=\"%s\"" realm)}})

;(defn email-web
;  []
;  (fn [{:keys [params ::friend/auth-config ::stripe/stripe-fn] :as request}]
;    (let [credential-fn (get auth-config :credential-fn)
;            login-uri (get auth-config :email-login-uri)]
;
;      ; Verify that we're doing a request to /api/login/email otherwise skip this flow
;      (when (and (= (path-info request)
;                    login-uri))
;        (cond
;          ; The user is coming from their email trying to verify the login, so try to login.
;          (:uuid params)
;          (try
;            (let [user-record (credential-fn
;                                (with-meta (assoc params :stripe-fn stripe-fn)
;                                           {::friend/workflow :form}))]
;              ; Successful login!
;              (debug "Login successful: " (:username user-record))
;              (workflows/make-auth user-record {::friend/workflow          :form
;                                                ::friend/redirect-on-auth? true}))
;            (catch ExceptionInfo e
;              (redirect-login-failed (:uuid params))))
;
;          true
;          (redirect-login-failed "Not found"))))))

;(defn email-mobile
;  "Workflow for login with an email verification link via the om.next structure.
;  A parser is assoc'ed to the request if the mutation matches a login mutation. The parser is used here to fetch
;  params from the mutation and perform the auth using those params.
;
;  Returns an authentication map on successful auth, otherwise nil."
;  []
;  (fn [{:keys [login-parser ::friend/auth-config body] :as request}]
;    (let [{:keys [login-mutation-uri credential-fn]} auth-config]
;      ;; Check conditions for performing this workflow and skip if not met:
;      (when (and
;              ; Cond 1) URL path matches the url for authenticating the user, /api
;              (= (path-info request)
;                 login-mutation-uri)
;              ; Cond 2) Login parser has bees assoc'ed in the request to fetch login params from the mutation with.
;              (some? login-parser))
;        (let [parsed-res (login-parser {} (:query body))
;              email-params (:result (get parsed-res 'session.signin.email/verify))] ; Get params from the 'email/verify mutation result
;
;           ;Cond 4) For email verification workflow we need the verify-uuid for the user trying to auth.
;           ;If nil we're probably doing some other auth type, so skip this workflow.
;          (when-let [verify-uuid (:verify-uuid email-params)]
;            (try
;              (let [user-record (credential-fn
;                                  (with-meta {:uuid verify-uuid}
;                                             {::friend/workflow :form}))]
;                ; Successful login!
;                (debug "Email login successful for user: " (:username user-record))
;                (workflows/make-auth user-record
;                                     {::friend/workflow          :form
;                                      ::friend/redirect-on-auth? false}))
;              (catch ExceptionInfo e
;                (throw e)))))))))

;(defn facebook
;  "Workflow for login with Facebook via the om.next structure.
;  A parser is assoc'ed to the request if the mutation matches a login mutation. The parser is used here to fetch
;  params from the mutation and perform the auth using those params.
;
;  Returns an authentication map on successful auth, otherwise nil."
;  []
;  (fn [{:keys [login-parser ::friend/auth-config ::stripe/stripe-fn body ::fb/facebook-token-validator] :as request}]
;    (let [{:keys [login-mutation-uri credential-fn]} auth-config]
;      ;; Check conditions for performing this workflow and skip if not met:
;      (when (and
;              ; Cond 1) URL path matches the url for authenticating the user, /api
;              (= (path-info request)
;                 login-mutation-uri)
;              ; Cond 2) Login parser has bees assoc'ed in the request to fetch login params from the mutation with.
;              (some? login-parser)
;              ; Cond 3) A facebook validator function is assoc'ed to the request to validate the fb token.
;              (some? facebook-token-validator))
;        (let [parsed-res (login-parser {} (:query body))
;              fb-params (:result (get parsed-res 'session.signin/facebook))] ; Get params from the 'signin/facebook mutation result
;
;          ; Cond 4) For facebook login workflow we need the user-id and access-token for the user trying to auth.
;          ; If nil we're probably doing some other auth type, so skip this workflow.
;          (when (some? fb-params)
;            (let [validated-token (facebook-token-validator fb-params)]
;              (if (:error validated-token)
;                (throw (ex-info "Facebook login error. Validating access token failed."
;                                {}))
;                (try
;                  (let [user-record (credential-fn
;                                      (with-meta (assoc validated-token :stripe-fn stripe-fn)
;                                                 {::friend/workflow :facebook}))]
;                    (debug "Facebook login successful for user: " (:username user-record))
;                    ;; Successful login, return authentication map.
;                    (workflows/make-auth user-record {::friend/workflow :facebook
;                                                      ::friend/redirect-on-auth? false}))
;                  (catch ExceptionInfo e
;                    (throw e)))))))))))

;(defn create-account
;  []
;  (fn [{:keys [login-parser body ::friend/auth-config ::stripe/stripe-fn] :as request}]
;    (let [{:keys [login-mutation-uri credential-fn]} auth-config]
;      (when (and
;              (= (path-info request)
;                 login-mutation-uri)
;              (some? login-parser))
;        (let [parsed-res (login-parser {} (:query body))
;              params (:result (get parsed-res 'session.signin/activate))]
;          (when (some? params)
;            (try
;              (let [user-record (credential-fn (with-meta (assoc params :stripe-fn stripe-fn)
;                                                          {::friend/workflow :activate-account}))]
;                (debug "Successful login")
;                (workflows/make-auth user-record {::friend/workflow          :activate-account
;                                                  ::friend/redirect-on-auth? false}))
;              (catch ExceptionInfo e
;                (let [{:keys [data]} (ex-data e)
;                      {:keys [verification]} data]
;                  (when verification
;                    (go
;                      ((:eponai.server.email/send-verification-fn request)
;                        verification {:user/status :user.status/new
;                                      :device      :web})))
;                  (when-not (::create-account-without-throwing request)
;                    (throw e)))))))))))