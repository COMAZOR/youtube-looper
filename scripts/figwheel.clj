(require '[figwheel-sidecar.repl :as r]
         '[figwheel-sidecar.repl-api :as ra])

(ra/start-figwheel!
  {:figwheel-options {:server-port 3449}
   :build-ids        ["chrome"]
   :all-builds       (figwheel-sidecar.system/get-project-builds)})

(ra/cljs-repl)
