(ns environ.sentry
  (:require
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [clojure.set :as set]
    [environ.core :as e]))


;; ## Var Declaration

(def known-vars
  "Map of environment keywords to a definition map which may contain a
  `:description` and optionally a `:type` for auto-coercion."
  {})


(defn declare-env-var!
  "Helper function for the `defenv` macro."
  [env-key properties]
  (when-let [extant (get known-vars env-key)]
    (log/errorf "Environment variable definition for %s in %s:%d is overriding existing definition in %s:%d"
                env-key (:ns properties) (:line properties) (:ns extant) (:line extant)))
  (-> #'known-vars
      (alter-var-root assoc env-key properties)
      (get env-key)))


(defmacro defenv
  "Define a new environment variable used by the system."
  [env-key description & {:as opts}]
  `(declare-env-var!
     ~env-key
     (assoc ~opts
            :ns '~(symbol (str *ns*))
            :line ~(:line (meta &form))
            :description ~description)))



;; ## Sentry Behavior

(def behavior
  "Definition for how the sentry should behave in various situations."
  {})


; TODO: behavior setters



;; ## Environment Map

(def accesses
  "Atom containing access counts for all environment maps."
  (atom {}))


(deftype EnvironmentMap
  [config _meta]

  java.lang.Object

  (toString
    [this]
    (str "EnvironmentMap " config))

  (equals
    [this that]
    (boolean
      (or (identical? this that)
          (when (identical? (class this) (class that))
            (= config (.config ^EnvironmentMap that))))))

  (hashCode
    [this]
    (hash [(class this) config]))


  clojure.lang.IObj

  (meta
    [this]
    _meta)

  (withMeta
    [this meta-map]
    (EnvironmentMap. config meta-map))


  clojure.lang.IFn

  (invoke
    [this k]
    (.valAt this k))

  (invoke
    [this k not-found]
    (.valAt this k not-found))


  clojure.lang.ILookup

  (valAt
    [this k]
    (.valAt this k nil))

  (valAt
    [this k not-found]
    (swap! accesses update k (fnil inc 0))
    (get config k not-found))


  clojure.lang.IPersistentMap

  (count
    [this]
    (count config))

  (empty
    [this]
    (EnvironmentMap. (empty config) _meta))

  (cons
    [this element]
    (EnvironmentMap. (cons config element) _meta))

  (equiv
    [this that]
    (.equals this that))

  (containsKey
    [this k]
    (contains? config k))

  (entryAt
    [this k]
    (let [v (.valAt this k this)]
      (when-not (identical? this v)
        (clojure.lang.MapEntry. k v))))

  (seq
    [this]
    (seq config))

  (iterator
    [this]
    (clojure.lang.RT/iter (seq this)))

  (assoc
    [this k v]
    (EnvironmentMap. (assoc config k v) _meta))

  (without
    [this k]
    (EnvironmentMap. (dissoc config k) _meta)))


(defmethod print-method EnvironmentMap
  [v ^java.io.Writer w]
  (.write w (str v)))


;; Remove automatic constructor function.
(ns-unmap *ns* '->EnvironmentMap)


(defn env-map
  "Constructs a new environment map."
  ([]
   (env-map {}))
  ([config]
   {:pre [(map? config)]}
   (EnvironmentMap. config nil)))