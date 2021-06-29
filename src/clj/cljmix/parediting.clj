(ns cljmix.parediting)

;; code is data -> editing is a data op
;; - vs inference: philosophy
;; - vs whitespace lisp (!)
;; - https://www.draketo.de/light/english/wisp-lisp-indentation-preprocessor
;; - honorable mention: haskell is fine in the trivial cases but otherwise, no

;; definition
;; - can't know the value of any form
;; - does it _require_ homoiconicity?
;; - does it _require_ lisp? no, but the fewer syntax kinds the better

;; modes and editors
;; - emacs standard
;; - lispy
;; - cursive built-in
;; - calva built-in
;; quick runthrough

(defn f [& _])

;; wrap
1

;; raise
f

;; splice
f 1

;; barf
(f) 1

;; slurp
(f 1)

;; barf-back
2 (f 1)

;; slurp-back
(f 1)

;; kill
(f 1)

;; join
(f 1)

;; split
(f) (1)

;; splice-kill
(f 1)
(f 1)

;; move sexp
(f 1)

;; navigation
(let [xs [1 2 3]]
  (doseq [x xs]
    (println "hello" x)))

;; convolute (!)
;; wish i had this TBH
(let [a 1] (if 'x 3 #_cursor a))
(if 'x 3 (let [a 1] a))


;; language-specific tricks
;; - is threading structural editing? it is, right?
;; annoyances
;; - unbalancing
;; - second-class citizen
;; - context (strings!)
;; - special forms (sets!)
;; transcendant factors
;; - navigation outside forms
;; other languages
;; - ??? sorry, chumps. C syntax is a plague.
;; - write your own lisp wrapper; parens are god
;;