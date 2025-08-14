(ns bet-project.service.Nba
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client])
  (:import
   (java.time LocalDate)
   (java.time.format DateTimeFormatter)))

(defn today-date []
  (let [formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd")]
    (.format (LocalDate/now) formatter)))

;; (defn calculate-moneyline [moneyline-value]
;;   (if (= moneyline-value 0.0001)
;;     0
;;     (let [value (int moneyline-value)]
;;       (cond
;;         (< value 0) (inc (double (abs (/ 100 value))))
;;         (> value 0) (double (/ value 100))))))

(defn get-schedules-nba [request]
  (let [response (client/get "https://therundown-therundown-v1.p.rapidapi.com/sports/4/schedule"
                             {:headers {:x-rapidapi-key "3e36075547msh24537dc0606651ap103e05jsna0572db9e77c"
                                        :x-rapidapi-host "therundown-therundown-v1.p.rapidapi.com"}
                              :query-params {:limit "100"}})
        data (:body response)]
    {:status 200
     :body data}))

(defn obter-mercados-nba [request]
  (let [date (today-date)
        response (client/get (str "https://therundown-therundown-v1.p.rapidapi.com/sports/4/openers/" date)
                             {:headers {:x-rapidapi-key "8b7aaa01f5msh14e11a5a9881536p14b4b3jsn74e4cd56608c"
                                        :x-rapidapi-host "therundown-therundown-v1.p.rapidapi.com"}
                              :query-params {:offset ""
                                             :include "scores&include=all_periods"}})
        dados (:body response)]
    {:status 200 :body dados}))

(defn resultado-correto-nba [event-id palpite]
  (try
    (let [date (today-date)
          response (client/get (str "https://therundown-therundown-v1.p.rapidapi.com/sports/4/events/2024-12-04")
                               {:headers {:x-rapidapi-key "8b7aaa01f5msh14e11a5a9881536p14b4b3jsn74e4cd56608c"
                                          :x-rapidapi-host "therundown-therundown-v1.p.rapidapi.com"}
                                :query-params {:include "scores,lines"
                                               :affiliate_ids "2"
                                               :offset "0"}})
          dados (json/parse-string (:body response) true)
          eventos (:events dados)
          evento (some #(when (= (:event_id %) event-id) %) eventos)]
      (if evento
        (let [score (:score evento)
              score-away (:score_away score)
              score-home (:score_home score)
              event-status (:event_status score)]
          (if (not= event-status "STATUS_FINAL")
            {:status 400
             :body "O evento ainda não terminou."}
            (if (and score-away score-home)
              (let [resultado-real (cond
                                     (> score-home score-away) "Casa"
                                     (< score-home score-away) "Visitante"
                                     :else "Empate")
                    acertou? (= palpite resultado-real)]
                {:status 200
                 :body {:score_home score-home
                        :score_away score-away
                        :resultado_real resultado-real
                        :palpite palpite
                        :acertou acertou?}})
              {:status 500
               :body "Dados de pontuação incompletos no evento."})))
        {:status 404
         :body "Evento não encontrado"}))
    (catch Exception e
      (println "Erro ao calcular resultado do evento:" (.getMessage e))
      {:status 500
       :body "Erro ao calcular resultado do evento."})))

(defn obter-eventos-nba [request]
  (let [date (today-date)
        response (client/get (str "https://therundown-therundown-v1.p.rapidapi.com/sports/4/events/2024-12-04")
                             {:headers {:x-rapidapi-key "8b7aaa01f5msh14e11a5a9881536p14b4b3jsn74e4cd56608c"
                                        :x-rapidapi-host "therundown-therundown-v1.p.rapidapi.com"}
                              :query-params {:include "scores"
                                             :affiliate_ids "2"
                                             :offset "0"}})
        dados (:body response)]
    {:status 200 :body dados}))

(defn calcular-over-under-nba [score-away score-home linha]

  (let [total-pontos (+ score-away score-home)]  
    (cond
      (> total-pontos linha) "Over"   
      (< total-pontos linha) "Under"  
      :else "Push")))                

(defn prever-over-under-nba [event-id palpite]

  (let [date (today-date)
        response (client/get (str "https://therundown-therundown-v1.p.rapidapi.com/sports/4/events/2024-12-04" )
                             {:headers {:x-rapidapi-key "8b7aaa01f5msh14e11a5a9881536p14b4b3jsn74e4cd56608c"
                                        :x-rapidapi-host "therundown-therundown-v1.p.rapidapi.com"}
                              :query-params {:include "scores,lines"
                                             :affiliate_ids "2"
                                             :offset "0"}})
        dados (json/parse-string (:body response) true)
        eventos (:events dados)
        evento (some #(when (= (:event_id %) event-id) %) eventos)]
    (if evento
      (let [score-away (:score_away (:score evento))
            score-home (:score_home (:score evento))
            event-status (:event_status evento)
            linha-over (get-in evento [:lines "2" :total :total_over])]
        (if (not= event-status "STATUS_FINAL")
          {:status 400
           :body "O evento ainda não terminou."}
          (if linha-over
            (let [resultado (calcular-over-under-nba score-away score-home linha-over)
                  acertou? (= resultado palpite)]  
              {:status 200
               :body {:score_away score-away
                      :score_home score-home
                      :linha_over linha-over
                      :resultado resultado
                      :palpite palpite
                      :acertou acertou?}})
            {:status 500
             :body "Linha de over/under não encontrada na API."})))
      {:status 404
       :body "Evento não encontrado"})))


;; Função comentada para obter a aposta
;; (defn obter-aposta-nba-handler [event-id tipo linha palpite]
;;   (cond
;;     (= tipo "resultado-correto")
;;     (let [resultado (resultado-correto-nba event-id palpite)]
;;       (if (= (:status resultado) 200)
;;         resultado
;;         {:status 400 :body "Erro ao processar a aposta de resultado correto."}))
;;     (= tipo "over-and-under")
;;     (let [resultado (prever-over-under-nba event-id linha)]
;;       (if (= (:status resultado) 200)
;;         resultado
;;         {:status 400 :body "Erro ao processar a aposta de over/under."}))
;;     :else
;;     {:status 400
;;      :body "Tipo de aposta inválido."}))
