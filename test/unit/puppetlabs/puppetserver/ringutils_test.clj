(ns puppetlabs.puppetserver.ringutils-test
  (:require [clojure.test :refer :all]
            [puppetlabs.puppetserver.ringutils :refer :all]
            [puppetlabs.ssl-utils.core :as ssl-utils]
            [schema.test :as schema-test]
            [cheshire.core :as cheshire]))

(use-fixtures :once schema-test/validate-schemas)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utilities

(def test-resources-dir
  "./dev-resources/puppetlabs/puppetserver/ringutils_test")

(defn test-pem-file
  [pem-file-name]
  (str test-resources-dir "/" pem-file-name))

(def localhost-cert
  (ssl-utils/pem->cert (test-pem-file "localhost-cert.pem")))

(def other-cert
  (ssl-utils/pem->cert (test-pem-file "revoked-agent.pem")))

(def base-handler
  (fn [request]
    {:status 200 :body "hello"}))

(defn build-ring-handler
  [whitelist-settings]
  (-> base-handler
      (wrap-with-cert-whitelist-check whitelist-settings)))

(defn test-request
  [cert]
  {:uri "/foo"
   :request-method :get
   :ssl-client-cert cert})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests

(deftest json-response-test
  (testing "json response"
    (let [source {"key1" "val1", "key2" "val2"}
          response (json-response source)]
      (testing "has 200 status code"
        (is (= 200 (:status response))))
      (testing "has json content-type"
        (is (= "application/json" (get-in response [:headers "Content-Type"]))))
      (testing "is properly converted to a json string"
        (is (= (cheshire/parse-string (:body response)) source))))))

(deftest wrap-with-cert-whitelist-check-test
  (let [ring-handler (build-ring-handler
                       {:client-whitelist ["localhost"]})]
    (testing "access allowed when cert is on whitelist"
      (let [response (ring-handler (test-request localhost-cert))]
        (is (= 200 (:status response)))
        (is (= "hello" (:body response)))))
    (testing "access denied when cert not on whitelist"
      (let [response (ring-handler (test-request other-cert))]
        (is (= 403 (:status response))))))
  (let [ring-handler (build-ring-handler
                       {:authorization-required false
                        :client-whitelist       []})]
    (testing "access allowed when auth not required"
      (let [response (ring-handler (test-request other-cert))]
        (is (= 200 (:status response)))
        (is (= "hello" (:body response)))))))
