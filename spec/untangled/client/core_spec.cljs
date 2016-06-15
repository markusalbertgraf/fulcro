(ns untangled.client.core-spec
  (:require
    [om.next :as om :refer-macros [defui]]
    [untangled.client.core :as uc]
    [untangled-spec.core :refer-macros
     [specification behavior assertions provided component when-mocking]]
    [om.next.protocols :as omp]))

(defui Child
  static om/Ident
  (ident [this props] [:child/by-id (:id props)])
  static om/IQuery
  (query [this] [:id :label]))

(defui Parent
  static uc/Constructor
  (uc/initial-state [this params] {:ui/checked true})
  static om/Ident
  (ident [this props] [:parent/by-id (:id props)])
  static om/IQuery
  (query [this] [:id :title {:child (om/get-query Child)}]))

(specification "merge-state!"
  (assertions
    "merge-query is the component query joined on it's ident"
    (#'uc/component-merge-query Parent {:id 42}) => [{[:parent/by-id 42] [:id :title {:child (om/get-query Child)}]}])
  (component "preprocessing the object to merge"
    (let [no-state (atom {:parent/by-id {}})
          no-state-merge-data (:merge-data (#'uc/preprocess-merge no-state Parent {:id 42}))
          state-with-old (atom {:parent/by-id {42 {:id 42 :title "Hello"}}})
          id [:parent/by-id 42]
          old-state-merge-data (:merge-data (#'uc/preprocess-merge state-with-old Parent {:id 42}))]
      (assertions
        "Uses the constructor of the component to generate base data when no existing object is in app state"
        (get-in no-state-merge-data [id :ui/checked]) => true
        "Uses the existing object in app state as base for merge when present (does not augment with constructor)"
        (get-in old-state-merge-data [id :ui/checked]) => nil
        "Marks fields that were queried but are not present as plumbing/not-found"
        old-state-merge-data => {[:parent/by-id 42] {:id    42
                                                     :title :untangled.client.impl.om-plumbing/not-found
                                                     :child :untangled.client.impl.om-plumbing/not-found}}))))

(specification "integrate-ident!"
  (let [state (atom {
                     :a    {:path [[:table 2]]}
                     :b    {:path [[:table 2]]}
                     :d    [:table 6]
                     :many {:path [[:table 99] [:table 88] [:table 77]]}
                     })]
    (when-mocking
      (omp/queue! r kw) => :ignored
      (om/app-state r) => state

      (behavior "Can append to an existing vector"
        (uc/integrate-ident! :reconciler [:table 3] :append [:a :path])
        (assertions
          (get-in @state [:a :path]) => [[:table 2] [:table 3]])
        (uc/integrate-ident! :reconciler [:table 3] :append [:a :path])
        (assertions
          "(is a no-op if the ident is already there)"
          (get-in @state [:a :path]) => [[:table 2] [:table 3]]))
      (behavior "Can prepend to an existing vector"
        (uc/integrate-ident! :reconciler [:table 3] :prepend [:b :path])
        (assertions
          (get-in @state [:b :path]) => [[:table 3] [:table 2]])
        (uc/integrate-ident! :reconciler [:table 3] :prepend [:b :path])
        (assertions
          "(is a no-op if already there)"
          (get-in @state [:b :path]) => [[:table 3] [:table 2]]))
      (behavior "Can create/replace a to-one ident"
        (uc/integrate-ident! :reconciler [:table 3] :replace [:c :path])
        (uc/integrate-ident! :reconciler [:table 3] :replace [:d])
        (assertions
          (get-in @state [:d]) => [:table 3]
          (get-in @state [:c :path]) => [:table 3]
          ))
      (behavior "Can replace an existing to-many element in a vector"
        (uc/integrate-ident! :reconciler [:table 3] :replace [:many :path 1])
        (assertions
          (get-in @state [:many :path]) => [[:table 99] [:table 3] [:table 77]])))))

