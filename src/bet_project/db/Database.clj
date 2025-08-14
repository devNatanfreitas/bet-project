(ns bet-project.db.Database
    (:require
     [bet-project.service.Nba :refer [prever-over-under-nba
                                      resultado-correto-nba]]
     [bet-project.service.NHL :refer [calcular-resultado-nhl
                                      prever-over-under-nhl]]
     [cheshire.core :as json]
   
     [clojure.java.jdbc :as jdbc]
     [clojure.string :as string]))

  (def db-spec
    {:dbtype   "mysql"
     :dbname   "bet"
     :host     "localhost"
     :port     3306
     :user     "root"
     :password "123456"})

  (defn create-apostas-table []
    (jdbc/execute! db-spec
                   ["CREATE TABLE IF NOT EXISTS apostas (
                     event_id VARCHAR(255) PRIMARY KEY NOT NULL,
                     quantidade INTEGER NOT NULL,
                     tipo VARCHAR(255) NOT NULL,
                     esporte VARCHAR(255) NOT NULL,
                     palpite VARCHAR(255) NULL,
                     evento VARCHAR(255) NULL,
                     data_aposta TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                   )"]))

    (defn create-apostasOdd-table []
    (jdbc/execute! db-spec
                   ["CREATE TABLE IF NOT EXISTS apostaodd (
                       event_id VARCHAR(255) PRIMARY KEY NOT NULL,
                        odd_home DECIMAL(15,2) NOT NULL DEFAULT 0.0,
                        odd_away DECIMAL(15,2) NOT NULL DEFAULT 0.0,
                        total_over DECIMAL(15,2) NOT NULL DEFAULT 0.0,
                       data_aposta TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                     )"]))

  (defn create-saldo-table []
    (jdbc/execute! db-spec
                   ["CREATE TABLE IF NOT EXISTS saldo (
                     id INT AUTO_INCREMENT PRIMARY KEY,
                     valor DECIMAL(15,2) NOT NULL DEFAULT 0.0
                   )"])
    (jdbc/execute! db-spec
                   ["INSERT INTO saldo (valor) VALUES (0.0) ON DUPLICATE KEY UPDATE id=id"]))

  (create-saldo-table)
  (create-apostas-table)
  (create-apostasOdd-table)

  (defn obter-saldo []
    (let [result (jdbc/query db-spec ["SELECT valor FROM saldo LIMIT 1"])]
      (:valor (first result))))

  (defn atualizar-saldo [quantidade]
    (jdbc/execute! db-spec ["UPDATE saldo SET valor = valor + ?" quantidade]))

  (defn inserir-aposta [event-id quantidade esporte tipo palpite evento]
    (let [query "INSERT INTO apostas (event_id, quantidade, esporte, tipo, palpite, evento)
                  VALUES (?, ?, ?, ?, ?, ?)"]
      (try
        (jdbc/execute! db-spec [query event-id quantidade esporte tipo
                                (when (and palpite (not (string/blank? palpite))) palpite)
                                evento])
        (println "Aposta inserida com sucesso.")
        (catch Exception e
          (println "Erro ao inserir aposta:" (.getMessage e))))))
  

  (defn inserir-odds [event-id odd-home odd-away total-over]
    (let [query "INSERT INTO apostaodd (event_id, odd_home, odd_away, total_over)
                  VALUES (?, ?, ?, ?)"]
      (try
        (jdbc/execute! db-spec [query event-id (or odd-home 0.0) (or odd-away 0.0) (or total-over 0.0)])
        (println "Odds inseridos com sucesso.")
        (catch Exception e
          (println "Erro ao inserir odds:" (.getMessage e))))))

  (defn obter-apostas []
    (let [results (jdbc/query db-spec ["SELECT * FROM apostas"])]
      (map (fn [aposta]
             {:event_id    (:event_id aposta)
              :quantidade  (:quantidade aposta)
              :tipo        (:tipo aposta)
              :esporte     (:esporte aposta)
              :palpite     (:palpite aposta)
              :odd_home    (:odd_home aposta)
              :odd_away    (:odd_away aposta)
              :total_over  (:total_over aposta)
              :data_aposta (:data_aposta aposta)})
           results)))

  (defn calcular-moneyline [moneyline-value]
    (let [value (double moneyline-value)]
      (if (= value 0.0001)
        0
        (cond
          (< value 0) (inc (Math/abs (/ 100 value)))
          (> value 0) (/ value 100)))))

  (defn calcular-ganho [quantidade moneyline]
    (* quantidade moneyline))

  (defn calcular-over-under-ganho [quantidade odd resultado palpite]
    (if (= resultado palpite)
      (calcular-ganho quantidade (calcular-moneyline odd))
      0))

(defn obter-odds [event-id]
  (let [results (jdbc/query db-spec ["SELECT * FROM apostasOdd WHERE event_id = ?" event-id])]
    (first results)))
  

  (defn obter-aposta-cal [_]
     (try
       (let [apostas (jdbc/query db-spec ["SELECT * FROM apostas"])
             resultados
             (map (fn [aposta]
                    (let [event-id      (:event_id aposta)
                          tipo          (:tipo aposta)
                          esporte       (:esporte aposta)
                          palpite       (:palpite aposta)
                          quantidade    (:quantidade aposta)
  
               
                          odds          (obter-odds event-id)
                          odd-home      (:odd_home odds)
                          odd-away      (:odd_away odds)
                          total-over    (:total_over odds)
  
                          ganho         (cond
                                          (and (= tipo "resultado-correto") (= esporte "basquete"))
                                          (let [{:keys [status body]} (resultado-correto-nba event-id palpite)]
                                            (when (= status 200)
                                              (cond
                                                (and (= true (:acertou body)) (> (:score_home body) (:score_away body)))
                                                (calcular-ganho quantidade (calcular-moneyline odd-home))
                                                (and (= true (:acertou body)) (< (:score_home body) (:score_away body)))
                                                (calcular-ganho quantidade (calcular-moneyline odd-away)))))
  
                                          (and (= tipo "resultado-correto") (= esporte "nhl"))
                                          (let [{:keys [status body]} (calcular-resultado-nhl event-id palpite)]
                                            (when (= status 200)
                                              (cond
                                                (and (= true (:acertou body)) (> (:score_home body) (:score_away body)))
                                                (calcular-ganho quantidade (calcular-moneyline odd-home))
                                                (and (= true (:acertou body)) (< (:score_home body) (:score_away body)))
                                                (calcular-ganho quantidade (calcular-moneyline odd-away)))))
  
                                          (and (= tipo "over-and-under") (= esporte "basquete"))
                                          (let [{:keys [status body]} (prever-over-under-nba event-id palpite)]
                                            (when (= status 200)
                                              (calcular-over-under-ganho quantidade total-over (:resultado body) palpite)))
  
                                          (and (= tipo "over-and-under") (= esporte "nhl"))
                                          (let [{:keys [status body]} (prever-over-under-nhl event-id palpite)]
                                            (when (= status 200)
                                              (calcular-over-under-ganho quantidade total-over (:resultado body) palpite)))
  
                                          :else 0)]
                      {:ganho    ganho
                       :event_id event-id
                       :tipo     tipo
                       :esporte  esporte
                       :palpite  palpite}))
                  apostas)
             resultados-validos (remove nil? resultados)
             total-ganho (reduce + (map :ganho resultados-validos))]
         (if (> total-ganho 0)
           {:status 200
            :body (json/generate-string {:mensagem (str "Você ganhou um total de R$ " total-ganho)})}
           {:status 200
            :body (json/generate-string {:mensagem "Você não ganhou nenhuma aposta."})})))
     )

