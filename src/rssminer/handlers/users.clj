(ns rssminer.handlers.users
  (:use  [ring.util.response :only [redirect]]
         [rssminer.config :only [cfg]]
         (rssminer [util :only [md5-sum valid-email? json-str2 read-if-json defhandler]]
                   [config :only [rssminer-conf cache-control]]))
  (:require [rssminer.db.user :as db]
            [rssminer.db.feed :as fdb]
            [rssminer.tmpls :as tmpls]
            [clojure.string :as str]))

(defn show-signup-page [req] (tmpls/signup))

(defhandler login [req email password return-url persistent mobile?]
  (let [user (db/authenticate email password)
        return-url (if (str/blank? return-url) "/a" return-url)]
    (if user
      (assoc (redirect return-url)
        :session {:id (:id user)}      ; IE does not persistent cookie
        )
      (if mobile?
        (tmpls/m-landing {:return-url return-url
                          :login-error true})
        (tmpls/landing {:return-url return-url
                        :login-error true})))))

(defn logout [req]
  (assoc (redirect "/")
    :session nil ;; delete cookie
    :session-cookie-attrs {:max-age -1}))

(defhandler signup [req email password]
  (cond (not (valid-email? email))
        (tmpls/signup {:error "Invalid Email"})
        (or (str/blank? password) (> 6 (count password)))
        (tmpls/signup {:error "Password at least 6 chars"})
        (db/find-by-email email)
        (tmpls/signup {:error "Email is already been token"})
        :else (let [user (db/create-user {:email email
                                          :password password})]
                (assoc (redirect "/a")           ; no conf currently
                  :session user))))

(defn- update-conf [uid req key]
  (when-let [data (-> req :body key)]
    (let [conf (merge (-> uid db/find-by-id :conf read-if-json)
                      {key data})]
      (db/update-user uid {:conf (json-str2 conf)}))))

(defhandler save-settings [req uid]
  (when-let [password (-> req :body :password)]
    (let [user (db/find-by-id uid)
          p (md5-sum (str (:email user) "+" password))]
      (db/update-user uid {:password p})))
  (update-conf uid req :nav)
  (update-conf uid req :pref_sort)
  {:status 204 :body nil})

;;; :nav => show and hide of left nav
;;; :pref_sort => show recommand or newest
(defhandler save-settings [req uid]
  (when-let [password (-> req :body :password)]
    (let [user (db/find-by-id uid)
          p (md5-sum (str (:email user) "+" password))]
      (db/update-user uid {:password p})))
  (update-conf uid req :nav)
  (update-conf uid req :pref_sort)
  {:status 204 :body nil})

(def demo-cache-newest (atom {}))

(defn- fetch-newest* [uid limit offset]
  (let [demo-id (:id (cfg :demo-user))]
    (if (= demo-id uid)
      (let [cache-key (str limit "-" offset)
            {:keys [ts data]} (@demo-cache-newest cache-key)
            now (System/currentTimeMillis)]
        (if (and data (< (- now ts) (* 3600 1000))) ; cache 1 hour
          data
          (let [data (fdb/fetch-newest uid limit offset)]
            (swap! demo-cache-newest assoc cache-key {:ts now
                                                      :data data})
            data)))
      (fdb/fetch-newest uid limit offset))))

(defhandler summary [req limit offset section uid]
  (let [data (case section
               "newest" (fetch-newest* uid limit offset)
               "voted" (fdb/fetch-vote uid limit offset)
               "read" (fdb/fetch-read uid limit offset))]
    (if (and (seq data) (not= "read" section) (not= "voted" section))
      {:body data ;; ok, just cache for 10 miniutes
       :headers cache-control}
      data)))

(defn google-openid [req]
  (let [spec "http://specs.openid.net/auth/2.0/identifier_select"
        url (str "https://www.google.com/accounts/o8/ud"
                 "?openid.ns=http://specs.openid.net/auth/2.0"
                 "&openid.ns.pape=http://specs.openid.net/extensions/pape/1.0"
                 "&openid.ns.max_auth_age=300"
                 "&openid.claimed_id=" spec
                 "&openid.identity=" spec
                 "&openid.mode=checkid_setup"
                 "&openid.ui.ns=http://specs.openid.net/extensions/ui/1.0"
                 "&openid.ui.mode=popup"
                 "&openid.ui.icon=true"
                 "&openid.ns.ax=http://openid.net/srv/ax/1.0"
                 "&openid.ax.mode=fetch_request"
                 "&openid.ax.type.email=http://axschema.org/contact/email"
                 "&openid.ax.required=email"
                 "&openid.return_to=http://rssminer.net/login/checkauth"
                 "&openid.realm=http://rssminer.net/")]
    (redirect url)))

(defn checkauth [req]
  (if-let [email ((:params req) "openid.ext1.value.email")]
    (assoc (redirect "/a")
      :session (or (db/find-by-email email)
                   (db/create-user {:email email
                                    :provider "google"})))
    (redirect "/")))
