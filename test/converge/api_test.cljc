(ns converge.api-test
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest is testing run-tests]])
            [converge.api :as convergent]))

(def a {:empty-m {}
        :empty-l []
        :a       :key
        :another {:nested {:key [1 2 3]}}
        :a-list  [:foo "bar" 'baz {:nested :inalist}]
        :a-set   #{1 2 3 4 5}})

(deftest convergent-ref-of-map
  (testing "Can initialize convergent ref with an empty map"
    (is (convergent/ref {})))
  (testing "Can initialize convergent ref with a non-empty map"
    (is (convergent/ref a)))
  (testing "Can reset to an empty map"
    (let [c (convergent/ref a)]
      (is (= {} (reset! c {})))))
  (testing "Can add a top-level key"
    (let [c (convergent/ref a)]
      (is (= (assoc a :foo :bar) (swap! c assoc :foo :bar)))))
  (testing "Can remove a top-level key"
    (let [c (convergent/ref a)]
      (is (= (assoc a :foo :bar) (swap! c assoc :foo :bar)))))
  (testing "Can add a nested key"
    (let [c (convergent/ref a)]
      (is (= (assoc-in a [:foo :bar] :baz)
             (swap! c assoc-in [:foo :bar] :baz)))))
  (testing "Can add a nested vector"
    (let [c (convergent/ref a)]
      (is (= (assoc a :foo [:baz])
             (swap! c assoc :foo [:baz])))))
  (testing "Can update a nested vector"
    (let [c (convergent/ref a)]
      (is (= (update a :empty-l conj :baz)
             (swap! c update :empty-l conj :baz)))))
  (testing "Can update deeply nested path"
    (let [c (convergent/ref a)]
      (is (= (update-in a [:a-list 3] assoc :foo :bar)
             (swap! c update-in [:a-list 3] assoc :foo :bar))))))

(deftest merging
  (let [c (convergent/ref a)
        d (convergent/ref-from-opset (convergent/opset c))]
      (swap! d assoc :b :another-key)
      (testing "merging another convergent ref"
        (is (= @d @(convergent/merge! c d))))
      (testing "merging a patch"
        (is (= @d @(convergent/merge! c (convergent/peek-patches d)))))))
