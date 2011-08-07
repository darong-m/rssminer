(ns freader.views.users-test
  (:use clojure.test
        (freader [test-common :only [test-app h2-fixture]])))

(use-fixtures :each h2-fixture)

(deftest test-login-sign-page
  (let [resp (test-app {:uri "/login"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (re-find #"name=\"email" (:body resp)))
    (is (re-find #"name=\"password" (:body resp))))
  (let [resp (test-app {:uri "/signup"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (re-find #"name=\"email" (:body resp)))
    (is (re-find #"name=\"password" (:body resp)))))

(deftest test-signup-login-process
  (let [params {"email" "test@test.com"
                "password" "123456"}
        signup (test-app {:request-method :post
                          :params params
                          :uri "/signup"})
        login (test-app {:request-method :post
                         :params params
                         :uri "/login"})
        remerber-me (test-app {:request-method :post
                               :params (assoc params
                                         "persistent" "on")
                               :uri "/login"})]
    (is (not (re-find #"Expires="
                      (first ((:headers login) "Set-Cookie")))))
    (is (re-find #"Expires="
                 (first ((:headers remerber-me) "Set-Cookie"))))
    (is (= 302 (:status signup)))
    (is (= 302 (:status login)))))
