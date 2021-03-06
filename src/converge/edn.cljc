;; Copyright 2020 Evident Systems LLC

;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at

;;     http://www.apache.org/licenses/LICENSE-2.0

;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
(ns converge.edn
  "API for interpreting an OpSet as EDN data."
  (:require [clojure.zip :as zip]
            [converge.opset :as opset]
            [converge.interpret :as interpret]
            [converge.util :as util]))

#?(:clj  (set! *warn-on-reflection* true)
   :cljs (set! *warn-on-infer* true))

(defn insertion-index
  [list-links entity attribute]
  (if-let [init-id (get list-links entity)]
    (loop [id init-id
           i  0]
      (if (= id attribute)
        i
        (recur (get list-links id)
               (inc i))))
    []))

(defn list-insertions
  [list-links entity]
  (if-let [init-id (get list-links entity)]
    (loop [id  init-id
           ins []]
      (if (= id interpret/list-end-sigil)
        ins
        (recur (get list-links id)
               (conj ins id))))
    []))

(defn interpretation-zip
  ([interpretation]
   (interpretation-zip interpretation opset/root-id))
  ([interpretation root-id]
   (let [elements   (:elements interpretation)
         list-links (:list-links interpretation)
         grouped    (group-by :entity (vals elements))]
     (zip/zipper
      (fn [node] (-> node :children seq boolean))
      (fn [node]
        (for [{:keys [entity attribute value]}
              (:children node)]
          (let [v        (get elements value)
                a        (if (opset/id? attribute)
                           (insertion-index list-links entity attribute)
                           attribute)
                children (get grouped value)]
            {:path       (conj (:path node) a)
             :entity     value
             :attribute  attribute
             :value      v
             :insertions (list-insertions list-links value)
             :children   children})))
      (constantly nil) ;; TODO: if we need to "mutate" the zipper itself
      {:entity     root-id
       :value      (get elements root-id)
       :children   (get grouped root-id)
       :insertions (list-insertions list-links root-id)
       :path       []}))))

(defn assoc-resizing
  ([vtr k v]
   (assoc-resizing vtr k v 0))
  ([vtr k v fill]
   (let [vtr-n (count vtr)
         vtr*  (if (> k vtr-n)
                 (into vtr (take (- k vtr-n) (repeat fill)))
                 vtr)]
     (assoc vtr* k v))))

(defn add-element
  [return path value]
  (let [attribute (peek path)]
    (if attribute
      (let [parent-path (pop path)
            parent      (util/safe-get-in return parent-path)]
        (if (sequential? parent)
          (if (seq parent-path)
            (update-in return parent-path (fnil assoc-resizing []) attribute value)
            ((fnil assoc-resizing []) return attribute value))
          (assoc-in return path value)))
      value)))

(defn assemble-values
  [{:keys [list-links] :as interpretation}]
  (loop [loc    (interpretation-zip interpretation opset/root-id)
         return nil]
    (if (zip/end? loc)
      return
      (let [{:keys [path entity insertions]
             v     :value}
            (zip/node loc)]
        (recur (zip/next loc)
               (add-element return
                            path
                            (cond-> v
                              (sequential? v)
                              (vary-meta assoc :converge/id entity :converge/insertions insertions)

                              (map? v)
                              (vary-meta assoc :converge/id entity))))))))

(defn edn
  [opset]
  (if (= (count opset) 0)
    nil
    (assemble-values (interpret/interpret opset))))
