(ns bet-project.core
  (:require
   [bet-project.db.Database :refer [obter-aposta-cal]]
   [bet-project.service.Aposta :refer [obter-aposta-handler
                                       registrar-aposta-handler
                                       registrar-aposta-odd-handler]]
   [bet-project.service.Financeiro :refer [depositar-handler
                                           obter-saldo-handler]]
   [bet-project.service.Nba :refer [get-schedules-nba obter-eventos-nba
                                    obter-mercados-nba]]
   [bet-project.service.NHL :refer [obter-eventos-nhl obter-mercados-nhl]]
   [cheshire.core :as json]
   [clj-http.client :as client]
   [io.pedestal.http :as http]
   [io.pedestal.http.route :as route]))



(def rotas
  (route/expand-routes
   #{["/depositar" :post depositar-handler :route-name :depositar]
     ["/saldo" :get obter-saldo-handler :route-name :saldo] 
     ["/apostar" :post registrar-aposta-handler :route-name :registrar-aposta]
     ["/liquidaposta" :get obter-aposta-cal :route-name :obter-apostas-cal]
     ["/aposta" :get obter-aposta-handler :route-name :obter-apostas]
     ["/eventos-nba" :get obter-eventos-nba :route-name :eventos-nba]
     ["/mercados-nba" :get obter-mercados-nba :route-name :mercados-nba]
     ["/mercados-nhl" :get obter-mercados-nhl :route-name :mercados-nhl]
     ["/schedules-nba" :get get-schedules-nba :route-name :get-nba-schedules]
     ["/register-odd" :post registrar-aposta-odd-handler :route-name :registrar-aposta-odd] 
     ["/events-nhl" :get obter-eventos-nhl :route-name :events-fut]
     }))

(def mapa-servico
  {::http/routes rotas
   ::http/port   9999
   ::http/type   :jetty
   ::http/join?  false})

(declare menu-principal)

(defn mostrar-menu []
  (println "\n====== Sistema de Apostas ======")
  (println "1. Gerenciar Conta")
  (println "2. Fazer Aposta")
  (println "3. Consultar Resultados")
  (println "4. Sair")
  (println "================================")
  (print "Escolha uma opcao: "))


(defn gerenciar-conta []
  (println "\n====== Gerenciar Conta ======")
  (println "1. Consultar Saldo")
  (println "2. Depositar")
  (println "3. Voltar")
  (print "Escolha uma opcao: ")
  (let [opcao (read-line)]
    (cond
      (= opcao "1")
      (let [response (client/get "http://localhost:9999/saldo")]
        (println "Seu saldo atual e: RS" (:saldo (json/parse-string (:body response) true))))

      (= opcao "2")
      (do
        (print "Digite o valor a depositar: ")
        (let [quantidade (Double/parseDouble (read-line))
              response (client/post "http://localhost:9999/depositar"
                                    {:body (json/generate-string {:quantidade quantidade})
                                     :headers {"Content-Type" "application/json"}})]
          (println "Depósito realizado com sucesso!")
          (println "Saldo atualizado:" (:saldo (json/parse-string (:body response) true)))))


      (= opcao "3") (println "Voltando ao menu principal...")
      :else (println "Opção invalida!")))
  (menu-principal))

(defn fazer-aposta []
  (println "\n====== Fazer Aposta ======")
  (print "Digite o ID do evento: ")
  (let [event-id (read-line)]
    (print "Digite o valor da aposta: ")
    (let [quantidade (Double/parseDouble (read-line))
          esporte (do (print "Esporte (futebol/basquete): ") (read-line))
          tipo (do (print "Tipo de aposta (resultado-correto/over-and-under): ") (read-line))
          palpite (if (= tipo "resultado-correto")
                    (do (print "Digite o palpite (Casa/Visitante/Empate): ") (read-line))
                    nil)
          evento (do (print "Digite o evento: ") (read-line))
         
          response (client/post "http://localhost:9999/apostar"
                                {:body (json/generate-string
                                        {:event-id event-id
                                         :quantidade quantidade
                                         :esporte esporte
                                         :tipo tipo
                                         :palpite palpite
                                         :evento evento})
                                 :headers {"Content-Type" "application/json"}})]
      (println "Resultado:" (:mensagem (json/parse-string (:body response) true)))))
  (menu-principal))

(defn consultar-resultados []
  (println "\n====== Consultar Resultados ======")
  (let [response (client/get "http://localhost:9999/aposta")
        apostas (json/parse-string (:body response) true)]
    (dorun
     (map #(println (str "Evento ID: " (:event_id %)
                         ", Quantidade: " (:quantidade %)
                         ", Tipo: " (:tipo %)
                         ", Esporte: " (:esporte %)
                         ", Palpite: " (:palpite %) 
                         ", Data da Aposta: " (:data_aposta %)))
          apostas)))
  (menu-principal))

(defn menu-principal []
  (mostrar-menu)
  (let [opcao (read-line)]
    (cond
      (= opcao "1") (gerenciar-conta)
      (= opcao "2") (fazer-aposta)
      (= opcao "3") (consultar-resultados)
      (= opcao "4") (println "Saindo do sistema...")
      :else (do (println "Opcao invalida!") (menu-principal)))))

(defn -main []
  (http/start (http/create-server mapa-servico))
  (menu-principal))
