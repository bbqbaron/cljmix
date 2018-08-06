(ns cljmix.core-test
  (:require [cljs.test :as t]
            [cljmix.core :as c]))

(t/deftest test-query-string
  (t/is (=
          (c/to-query-string c/my-query)
          "")))



