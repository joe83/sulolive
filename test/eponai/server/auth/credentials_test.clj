(ns eponai.server.auth.credentials-test
  (:require [cemerick.friend :as friend]
            [clojure.test :refer :all]
            [datomic.api :as d]
            [eponai.server.datomic.format :as f]
            [eponai.server.auth.credentials :as a]
            [eponai.server.datomic.pull :as p]
            [eponai.server.test-util :refer [new-db]])
  (:import (clojure.lang ExceptionInfo)))

(def schema (read-string (slurp "resources/private/datomic-schema.edn")))

(defn test-fb-info
  ([]
   (test-fb-info "fb-user-id" "access-token"))
  ([id _]
   {:name  "Facebook name"
    :email "email@email.com"
    :id    id}))

(def email "email@email.com")

(defn creds-input
  ([user-id]
    (creds-input user-id test-fb-info))
  ([user-id fb-info-fn]
   (with-meta {:user_id      user-id
               :access_token "access-token"
               :fb-info-fn   fb-info-fn}
              {::friend/workflow :facebook})))

;; -------- FB Credential function tests

(deftest fb-user-and-activated-user-account-with-email-exist
  (testing "Fb user exists and is connected to an existing activated user account, should log in."
    (let [{:keys [email id]} (test-fb-info)
          activated-user (assoc (f/user->db-user email) :user/status :user.status/activated)
          fb-user (f/fb-user-db-user id "access-token" activated-user)
          conn (new-db [activated-user fb-user])
          credential-fn (a/credential-fn conn)
          user-record (credential-fn (creds-input id))]

      ; We're authenticated.
      (is (= user-record
             (a/auth-map-for-db-user (p/user (d/db conn) (:user/email activated-user))))))))

(deftest fb-user-exists-but-no-user-account-with-matching-email
  (testing "FB account exists, but a matching user account does not. Should prompt user to create an account
  (can happen if the FB account does not have an email)."
    (let [{:keys [_ id]} (test-fb-info)
          db-user (f/user->db-user)
          fb-user (f/fb-user-db-user id "access-token" db-user)
          conn (new-db [fb-user])
          credential-fn (a/credential-fn conn)]

      (is (thrown-with-msg? ExceptionInfo
                            (re-pattern (.getMessage (a/ex-user-not-activated nil)))
                            (credential-fn
                              (creds-input id)))))))

(deftest fb-user-not-exist-but-activated-user-account-with-email-does
  (testing "The FB account does not exist, but a user account with the same email does.
  Should create a new fb-user and link to the existing account."
    (let [{:keys [email id]} (test-fb-info)
          activated-user (assoc (f/user->db-user email) :user/status :user.status/activated)
          conn (new-db [activated-user])
          credential-fn (a/credential-fn conn)
          user-record (credential-fn (creds-input id))
          ; Entities added to the DB
          db-user (p/user (d/db conn) email)
          fb-user (p/fb-user (d/db conn) id)]

      ; We're authenticated
      (is (= user-record
             (a/auth-map-for-db-user db-user)))
      ;The FB user is added to the DB and linked to the db-user
      (is fb-user)
      ; The created FB user is linked to the user account with the same email.
      (is (= (:db/id (:fb-user/user fb-user))
             (:db/id db-user))))))

(deftest new-fb-user-has-email-email-and-user-account-does-not-exist
  (testing "Neither FB user (with email) or user account with the mathing email exists,
  create a new FB user and link to a new account."
    (let [{:keys [email id]} (test-fb-info)
          conn (new-db)
          credential-fn (a/credential-fn conn)]

      ;TODO we might want to automatically create an ccount here and let the user login immediately?
      (is (thrown-with-msg? ExceptionInfo
                            (re-pattern (.getMessage (a/ex-user-not-activated nil)))
                            (credential-fn (creds-input id))))
      ; New user account with the Facebook user's email should be created
      (let [{:keys [fb-user/user]} (p/fb-user (d/db conn) id)]
        (is (= (:user/email (d/entity (d/db conn) (:db/id user)))
               email))))))

(deftest fb-user-without-email-does-not-exist
  (testing "Fb user does not have an email and is trying to sign up. New fb user linked to a new user should be created.
  Should be prompted to activate account."
    (let [fb-info-fn (fn [_ _]
                       (dissoc (test-fb-info) :email))
          {:keys [id]} (fb-info-fn nil nil)
          conn (new-db)
          credential-fn (a/credential-fn conn)]

      (is (thrown-with-msg? ExceptionInfo
                            (re-pattern (.getMessage (a/ex-user-not-activated nil)))
                            (credential-fn (creds-input id fb-info-fn))))
      ;; A new fb user should be created and link to a new user account
      (let [fb-user (p/fb-user (d/db conn) id)]
        (is fb-user)
        (is (:fb-user/user fb-user))))))


;; ------ User verify email credential function tests.
;; Covering everything relevat for :form credential-fn.
;; More detailed cases are covered for api/verify-email in api-test.clj

(deftest user-verifies-and-is-activated
  (testing "User verifies their email and is already activated, return auth map"
    (let [user (assoc (f/user->db-user email) :user/status :user.status/activated)
          verification (f/->db-email-verification user :verification.status/pending)
          conn (new-db [user
                        verification])
          credential-fn (a/credential-fn conn)]

      (is (= (credential-fn
               (with-meta {:uuid (str (:verification/uuid verification))}
                          {::friend/workflow :form}))
             (a/auth-map-for-db-user (p/user (d/db conn) email)))))))

; Failure cases
(deftest user-verifies-account-not-activated
  (testing "User is verified but not activated, should throw exception new user."
    (let [user (f/user->db-user email)
          verification (f/->db-email-verification user :verification.status/pending)
          conn (new-db [user
                        verification])
          credential-fn (a/credential-fn conn)]

      (is (thrown-with-msg? ExceptionInfo
                            (re-pattern (.getMessage (a/ex-user-not-activated nil)))
                            (credential-fn
                              (with-meta {:uuid (str (:verification/uuid verification))}
                                         {::friend/workflow :form})))))))

(deftest user-verifies-nil-uuid
  (testing "User tries to verify a nil UUID. Throw exception"
    (let [conn (new-db)
          credential-fn (a/credential-fn conn)]
      (is (thrown-with-msg? ExceptionInfo
                            (re-pattern (.getMessage (a/ex-invalid-input nil)))
                            (credential-fn
                              (with-meta {:invalid :data}
                                         {::friend/workflow :form})))))))

;; ------ User activate account credential function tests.
;; Covering everything relevat for :form credential-fn.
;; More detailed cases are covered for api/activate-account in api-test.clj

(deftest user-activates-account-with-verified-email
  (testing "User activates account with their email verified."
    (let [user (f/user->db-user email)
          verification (f/->db-email-verification user :verification.status/verified)
          conn (new-db [user
                        verification])
          credential-fn (a/credential-fn conn)]
      (is (= (credential-fn (with-meta {:user-uuid  (str (:user/uuid user))
                                        :user-email (:user/email user)}
                                       {::friend/workflow :activate-account}))
             (a/auth-map-for-db-user (p/user (d/db conn) email)))))))

(deftest user-activates-account-with-invalid-input
  (testing "Invalid input is coming in to the credential fn, should throw exception."
    (let [conn (new-db)
          credential-fn (a/credential-fn conn)]
      (is (thrown-with-msg? ExceptionInfo
                            (re-pattern (.getMessage (a/ex-invalid-input nil)))
                            (credential-fn
                              (with-meta {:invalid :data}
                                         {::friend/workflow :activate-account})))))))