(ns bet-project.service.Aposta
  (:require
   [bet-project.db.Database :refer [inserir-aposta inserir-odds obter-apostas]]
   [bet-project.service.Financeiro :refer [saldo-conta]]
   [cheshire.core :as json]))

(def apostas-odd (atom []))
(def apostas (atom []))

(defn salvar-apostas-no-banco []
  (dorun
   (map #(inserir-aposta (:event-id %)
                         (:quantidade %)
                         (:esporte %)
                         (:tipo %)
                         (:palpite %)
                         (:evento %))
        @apostas))
  (reset! apostas []))

(defn registrar-aposta-handler [request]
  (let [aposta        (json/parse-string (slurp (:body request)) true)
        event-id      (:event-id aposta)
        valor-aposta  (:quantidade aposta)
        esporte       (:esporte aposta)
        tipo-aposta   (:tipo aposta)
        palpite       (get aposta :palpite nil)
        evento        (get aposta :evento nil)]
    (if (and (number? valor-aposta) (<= valor-aposta @saldo-conta))
      (do
        (swap! saldo-conta - valor-aposta)
        (swap! apostas conj {:event-id    event-id
                             :quantidade  valor-aposta
                             :esporte     esporte
                             :tipo        tipo-aposta
                             :palpite     palpite
                             :evento      evento})
        (salvar-apostas-no-banco)
        {:status 200
         :body (json/generate-string {:mensagem "Aposta registrada com sucesso."
                                      :saldo    @saldo-conta})})
      {:status 400
       :body    "Saldo insuficiente ou valor da aposta inválido."})))


(defn salvar-apostas-odd-no-banco []
  (dorun
   (map #(inserir-odds (:event-id %) (:odd-home %) (:odd-away %) (:total-over %))
        @apostas-odd))
  (reset! apostas-odd []))


(defn registrar-aposta-odd-handler [request]
  (let [aposta (json/parse-string (slurp (:body request)) true)
        event-id (:event-id aposta)
        odd-home (:odd-home aposta)
        odd-away (:odd-away aposta)
        total-over (:total-over aposta)]
    (swap! apostas-odd conj {:event-id event-id
                             :odd-home odd-home
                             :odd-away odd-away
                             :total-over total-over})
    (salvar-apostas-odd-no-banco)
    {:status 200
     :body (json/generate-string {:mensagem "Aposta de odds registrada com sucesso."})}))

(defn obter-aposta-handler [request]
  (let [apostas (obter-apostas)]
    {:status 200
     :body   (json/generate-string apostas)}))