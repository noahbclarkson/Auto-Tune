//! Integration tests for Auto-Tune auction house API
//!
//! Tests the REST endpoints:
//! - POST /api/orders - placing buy/sell orders
//! - DELETE /api/orders/:id - cancellation
//! - GET /api/orders - listing with filters
//! - GET /api/orderbook/:item_id - order book aggregation

use actix_web::{test, web, App};
use serde_json::json;
use uuid::Uuid;

use api_server::matching::MatchingEngine;
use api_server::routes::orders::{cancel_order, get_orderbook, list_orders, place_order};

/// Helper to create a test app with the matching engine
fn create_test_app() -> App<
    impl actix_web::dev::ServiceFactory<
        actix_web::dev::ServiceRequest,
        Config = (),
        Response = actix_web::dev::ServiceResponse,
        Error = actix_web::Error,
        InitError = (),
    >,
> {
    let engine = std::sync::Arc::new(tokio::sync::RwLock::new(MatchingEngine::new()));
    App::new()
        .app_data(web::Data::new(engine))
        .route("/api/orders", web::post().to(place_order))
        .route("/api/orders", web::get().to(list_orders))
        .route("/api/orders/{id}", web::delete().to(cancel_order))
        .route("/api/orderbook/{item_id}", web::get().to(get_orderbook))
}

// ---------------------------------------------------------------------------
// POST /api/orders - Placing orders
// ---------------------------------------------------------------------------

#[actix_web::test]
async fn test_place_buy_order_success() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();
    let req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "player1",
            "price": 100.0,
            "quantity": 10,
            "side": "buy"
        }))
        .to_request();

    let resp = test::call_service(&app, req).await;
    assert!(resp.status().is_success());

    let body: serde_json::Value = test::read_body_json(resp).await;
    assert!(body["order"].is_object());
    assert_eq!(body["order"]["item_id"], "DIAMOND");
    assert_eq!(body["order"]["player_id"], "player1");
    assert_eq!(body["order"]["price"], 100.0);
    assert_eq!(body["order"]["quantity"], 10);
    assert_eq!(body["order"]["side"], "buy");
    assert_eq!(body["order"]["status"], "open");
    assert_eq!(body["order"]["remaining_quantity"], 10);
    assert!(body["order"]["id"].is_string());
}

#[actix_web::test]
async fn test_place_sell_order_success() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();
    let req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "IRON_INGOT",
            "player_id": "player2",
            "price": 50.0,
            "quantity": 20,
            "side": "sell"
        }))
        .to_request();

    let resp = test::call_service(&app, req).await;
    assert!(resp.status().is_success());

    let body: serde_json::Value = test::read_body_json(resp).await;
    assert_eq!(body["order"]["side"], "sell");
    assert_eq!(body["order"]["status"], "open");
}

#[actix_web::test]
async fn test_place_order_invalid_price() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();
    let req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "player1",
            "price": 0.0,
            "quantity": 10,
            "side": "buy"
        }))
        .to_request();

    let resp = test::call_service(&app, req).await;
    assert_eq!(resp.status(), actix_web::http::StatusCode::BAD_REQUEST);
}

#[actix_web::test]
async fn test_place_order_invalid_quantity() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();
    let req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "player1",
            "price": 100.0,
            "quantity": 0,
            "side": "buy"
        }))
        .to_request();

    let resp = test::call_service(&app, req).await;
    assert_eq!(resp.status(), actix_web::http::StatusCode::BAD_REQUEST);
}

#[actix_web::test]
async fn test_place_order_invalid_side() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();
    let req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "player1",
            "price": 100.0,
            "quantity": 10,
            "side": "invalid_side"
        }))
        .to_request();

    let resp = test::call_service(&app, req).await;
    assert_eq!(resp.status(), actix_web::http::StatusCode::BAD_REQUEST);
}

#[actix_web::test]
async fn test_place_order_negative_price() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();
    let req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "player1",
            "price": -100.0,
            "quantity": 10,
            "side": "buy"
        }))
        .to_request();

    let resp = test::call_service(&app, req).await;
    assert_eq!(resp.status(), actix_web::http::StatusCode::BAD_REQUEST);
}

#[actix_web::test]
async fn test_place_order_matching() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();

    // Place a sell order
    let sell_req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "seller",
            "price": 100.0,
            "quantity": 10,
            "side": "sell"
        }))
        .to_request();

    let sell_resp = test::call_service(&app, sell_req).await;
    assert!(sell_resp.status().is_success());

    // Place a buy order that should match
    let buy_req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "buyer",
            "price": 105.0,
            "quantity": 5,
            "side": "buy"
        }))
        .to_request();

    let buy_resp = test::call_service(&app, buy_req).await;
    assert!(buy_resp.status().is_success());

    let body: serde_json::Value = test::read_body_json(buy_resp).await;
    // Buy order should be fully filled
    assert_eq!(body["order"]["status"], "filled");
    assert_eq!(body["order"]["remaining_quantity"], 0);
}

// ---------------------------------------------------------------------------
// DELETE /api/orders/:id - Canceling orders
// ---------------------------------------------------------------------------

#[actix_web::test]
async fn test_cancel_order_success() {
    let app = test::init_service(create_test_app()).await;

    // First place an order
    let server_id = Uuid::new_v4();
    let place_req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "player1",
            "price": 100.0,
            "quantity": 10,
            "side": "buy"
        }))
        .to_request();

    let place_resp = test::call_service(&app, place_req).await;
    let place_body: serde_json::Value = test::read_body_json(place_resp).await;
    let order_id = place_body["order"]["id"].as_str().unwrap();

    // Now cancel it
    let cancel_req = test::TestRequest::delete()
        .uri(&format!("/api/orders/{}?player_id=player1", order_id))
        .to_request();

    let cancel_resp = test::call_service(&app, cancel_req).await;
    assert!(cancel_resp.status().is_success());

    let cancel_body: serde_json::Value = test::read_body_json(cancel_resp).await;
    assert_eq!(cancel_body["status"], "cancelled");
}

#[actix_web::test]
async fn test_cancel_order_not_found() {
    let app = test::init_service(create_test_app()).await;

    let fake_order_id = Uuid::new_v4();
    let cancel_req = test::TestRequest::delete()
        .uri(&format!("/api/orders/{}?player_id=player1", fake_order_id))
        .to_request();

    let cancel_resp = test::call_service(&app, cancel_req).await;
    assert_eq!(cancel_resp.status(), actix_web::http::StatusCode::NOT_FOUND);
}

#[actix_web::test]
async fn test_cancel_order_wrong_player() {
    let app = test::init_service(create_test_app()).await;

    // Place order as player1
    let server_id = Uuid::new_v4();
    let place_req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "player1",
            "price": 100.0,
            "quantity": 10,
            "side": "buy"
        }))
        .to_request();

    let place_resp = test::call_service(&app, place_req).await;
    let place_body: serde_json::Value = test::read_body_json(place_resp).await;
    let order_id = place_body["order"]["id"].as_str().unwrap();

    // Try to cancel as player2 (wrong owner)
    let cancel_req = test::TestRequest::delete()
        .uri(&format!("/api/orders/{}?player_id=player2", order_id))
        .to_request();

    let cancel_resp = test::call_service(&app, cancel_req).await;
    assert_eq!(cancel_resp.status(), actix_web::http::StatusCode::NOT_FOUND);
}

#[actix_web::test]
async fn test_cancel_order_missing_player_id() {
    let app = test::init_service(create_test_app()).await;

    // Place order
    let server_id = Uuid::new_v4();
    let place_req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "player1",
            "price": 100.0,
            "quantity": 10,
            "side": "buy"
        }))
        .to_request();

    let place_resp = test::call_service(&app, place_req).await;
    let place_body: serde_json::Value = test::read_body_json(place_resp).await;
    let order_id = place_body["order"]["id"].as_str().unwrap();

    // Try to cancel without player_id query parameter
    let cancel_req = test::TestRequest::delete()
        .uri(&format!("/api/orders/{}", order_id))
        .to_request();

    let cancel_resp = test::call_service(&app, cancel_req).await;
    assert_eq!(
        cancel_resp.status(),
        actix_web::http::StatusCode::BAD_REQUEST
    );
}

#[actix_web::test]
async fn test_cancel_already_filled_order() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();

    // Place sell order
    let sell_req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "seller",
            "price": 100.0,
            "quantity": 10,
            "side": "sell"
        }))
        .to_request();

    let sell_resp = test::call_service(&app, sell_req).await;
    let sell_body: serde_json::Value = test::read_body_json(sell_resp).await;
    let sell_order_id = sell_body["order"]["id"].as_str().unwrap();

    // Place buy order that fully matches
    let buy_req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "buyer",
            "price": 105.0,
            "quantity": 10,
            "side": "buy"
        }))
        .to_request();

    test::call_service(&app, buy_req).await;

    // Try to cancel the already-filled sell order
    let cancel_req = test::TestRequest::delete()
        .uri(&format!("/api/orders/{}?player_id=seller", sell_order_id))
        .to_request();

    let cancel_resp = test::call_service(&app, cancel_req).await;
    assert_eq!(
        cancel_resp.status(),
        actix_web::http::StatusCode::BAD_REQUEST
    );
}

// ---------------------------------------------------------------------------
// GET /api/orders - Listing orders
// ---------------------------------------------------------------------------

#[actix_web::test]
async fn test_list_all_orders() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();

    // Place multiple orders
    for i in 0..3 {
        let req = test::TestRequest::post()
            .uri("/api/orders")
            .set_json(&json!({
                "server_id": server_id,
                "item_id": format!("ITEM_{}", i),
                "player_id": format!("player{}", i),
                "price": 100.0 + i as f64 * 10.0,
                "quantity": 10,
                "side": "buy"
            }))
            .to_request();
        test::call_service(&app, req).await;
    }

    // List all orders
    let list_req = test::TestRequest::get().uri("/api/orders").to_request();

    let list_resp = test::call_service(&app, list_req).await;
    assert!(list_resp.status().is_success());

    let body: serde_json::Value = test::read_body_json(list_resp).await;
    assert!(body["orders"].is_array());
    assert_eq!(body["orders"].as_array().unwrap().len(), 3);
}

#[actix_web::test]
async fn test_list_orders_by_player() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();

    // Place orders for different players
    for i in 0..3 {
        let req = test::TestRequest::post()
            .uri("/api/orders")
            .set_json(&json!({
                "server_id": server_id,
                "item_id": "DIAMOND",
                "player_id": format!("player{}", i),
                "price": 100.0,
                "quantity": 10,
                "side": "buy"
            }))
            .to_request();
        test::call_service(&app, req).await;
    }

    // List orders for player1 only
    let list_req = test::TestRequest::get()
        .uri("/api/orders?player_id=player1")
        .to_request();

    let list_resp = test::call_service(&app, list_req).await;
    assert!(list_resp.status().is_success());

    let body: serde_json::Value = test::read_body_json(list_resp).await;
    let orders = body["orders"].as_array().unwrap();
    assert_eq!(orders.len(), 1);
    assert_eq!(orders[0]["player_id"], "player1");
}

#[actix_web::test]
async fn test_list_orders_excludes_non_active() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();

    // Place and cancel an order
    let place_req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "player1",
            "price": 100.0,
            "quantity": 10,
            "side": "buy"
        }))
        .to_request();

    let place_resp = test::call_service(&app, place_req).await;
    let place_body: serde_json::Value = test::read_body_json(place_resp).await;
    let order_id = place_body["order"]["id"].as_str().unwrap();

    // Cancel it
    let cancel_req = test::TestRequest::delete()
        .uri(&format!("/api/orders/{}?player_id=player1", order_id))
        .to_request();
    test::call_service(&app, cancel_req).await;

    // List orders - should be empty
    let list_req = test::TestRequest::get().uri("/api/orders").to_request();

    let list_resp = test::call_service(&app, list_req).await;
    let body: serde_json::Value = test::read_body_json(list_resp).await;
    assert_eq!(body["orders"].as_array().unwrap().len(), 0);
}

#[actix_web::test]
async fn test_list_orders_empty() {
    let app = test::init_service(create_test_app()).await;

    let list_req = test::TestRequest::get().uri("/api/orders").to_request();

    let list_resp = test::call_service(&app, list_req).await;
    assert!(list_resp.status().is_success());

    let body: serde_json::Value = test::read_body_json(list_resp).await;
    assert_eq!(body["orders"].as_array().unwrap().len(), 0);
}

// ---------------------------------------------------------------------------
// GET /api/orderbook/:item_id - Order book aggregation
// ---------------------------------------------------------------------------

#[actix_web::test]
async fn test_get_orderbook_empty() {
    let app = test::init_service(create_test_app()).await;

    let req = test::TestRequest::get()
        .uri("/api/orderbook/DIAMOND")
        .to_request();

    let resp = test::call_service(&app, req).await;
    assert!(resp.status().is_success());

    let body: serde_json::Value = test::read_body_json(resp).await;
    assert_eq!(body["item_id"], "DIAMOND");
    assert!(body["buys"].as_array().unwrap().is_empty());
    assert!(body["sells"].as_array().unwrap().is_empty());
}

#[actix_web::test]
async fn test_get_orderbook_with_orders() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();

    // Place buy orders at different prices
    for i in 0..3 {
        let req = test::TestRequest::post()
            .uri("/api/orders")
            .set_json(&json!({
                "server_id": server_id,
                "item_id": "DIAMOND",
                "player_id": format!("buyer{}", i),
                "price": 100.0 + i as f64 * 10.0,
                "quantity": 10,
                "side": "buy"
            }))
            .to_request();
        test::call_service(&app, req).await;
    }

    // Place sell orders at different prices
    for i in 0..2 {
        let req = test::TestRequest::post()
            .uri("/api/orders")
            .set_json(&json!({
                "server_id": server_id,
                "item_id": "DIAMOND",
                "player_id": format!("seller{}", i),
                "price": 150.0 + i as f64 * 10.0,
                "quantity": 15,
                "side": "sell"
            }))
            .to_request();
        test::call_service(&app, req).await;
    }

    // Get order book
    let req = test::TestRequest::get()
        .uri("/api/orderbook/DIAMOND")
        .to_request();

    let resp = test::call_service(&app, req).await;
    assert!(resp.status().is_success());

    let body: serde_json::Value = test::read_body_json(resp).await;
    assert_eq!(body["item_id"], "DIAMOND");

    let buys = body["buys"].as_array().unwrap();
    let sells = body["sells"].as_array().unwrap();

    assert_eq!(buys.len(), 3);
    assert_eq!(sells.len(), 2);

    // Verify buy orders are sorted by price descending (highest first)
    for i in 0..buys.len() - 1 {
        let price1 = buys[i]["price"].as_f64().unwrap();
        let price2 = buys[i + 1]["price"].as_f64().unwrap();
        assert!(
            price1 >= price2,
            "Buy orders should be sorted by price descending"
        );
    }

    // Verify sell orders are sorted by price ascending (lowest first)
    for i in 0..sells.len() - 1 {
        let price1 = sells[i]["price"].as_f64().unwrap();
        let price2 = sells[i + 1]["price"].as_f64().unwrap();
        assert!(
            price1 <= price2,
            "Sell orders should be sorted by price ascending"
        );
    }
}

#[actix_web::test]
async fn test_get_orderbook_aggregation() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();

    // Place multiple orders at the same price level
    for i in 0..3 {
        let req = test::TestRequest::post()
            .uri("/api/orders")
            .set_json(&json!({
                "server_id": server_id,
                "item_id": "DIAMOND",
                "player_id": format!("buyer{}", i),
                "price": 100.0,
                "quantity": 10,
                "side": "buy"
            }))
            .to_request();
        test::call_service(&app, req).await;
    }

    // Get order book
    let req = test::TestRequest::get()
        .uri("/api/orderbook/DIAMOND")
        .to_request();

    let resp = test::call_service(&app, req).await;
    let body: serde_json::Value = test::read_body_json(resp).await;

    let buys = body["buys"].as_array().unwrap();

    // Should aggregate into one price level
    assert_eq!(buys.len(), 1);
    assert_eq!(buys[0]["price"], 100.0);
    assert_eq!(buys[0]["quantity"], 30); // 3 orders × 10 quantity each
    assert_eq!(buys[0]["orders"], 3); // 3 orders at this price level
}

#[actix_web::test]
async fn test_get_orderbook_different_items() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();

    // Place orders for DIAMOND
    let req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "player1",
            "price": 100.0,
            "quantity": 10,
            "side": "buy"
        }))
        .to_request();
    test::call_service(&app, req).await;

    // Place orders for IRON_INGOT
    let req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "IRON_INGOT",
            "player_id": "player2",
            "price": 50.0,
            "quantity": 20,
            "side": "buy"
        }))
        .to_request();
    test::call_service(&app, req).await;

    // Get order book for DIAMOND
    let req = test::TestRequest::get()
        .uri("/api/orderbook/DIAMOND")
        .to_request();
    let resp = test::call_service(&app, req).await;
    let diamond_book: serde_json::Value = test::read_body_json(resp).await;

    // Get order book for IRON_INGOT
    let req = test::TestRequest::get()
        .uri("/api/orderbook/IRON_INGOT")
        .to_request();
    let resp = test::call_service(&app, req).await;
    let iron_book: serde_json::Value = test::read_body_json(resp).await;

    // Verify they're separate
    assert_eq!(diamond_book["item_id"], "DIAMOND");
    assert_eq!(diamond_book["buys"].as_array().unwrap().len(), 1);

    assert_eq!(iron_book["item_id"], "IRON_INGOT");
    assert_eq!(iron_book["buys"].as_array().unwrap().len(), 1);
}

#[actix_web::test]
async fn test_get_orderbook_excludes_filled_orders() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();

    // Place a sell order
    let sell_req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "seller",
            "price": 100.0,
            "quantity": 10,
            "side": "sell"
        }))
        .to_request();
    test::call_service(&app, sell_req).await;

    // Place a buy order that matches fully
    let buy_req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "buyer",
            "price": 105.0,
            "quantity": 10,
            "side": "buy"
        }))
        .to_request();
    test::call_service(&app, buy_req).await;

    // Get order book - should be empty
    let req = test::TestRequest::get()
        .uri("/api/orderbook/DIAMOND")
        .to_request();
    let resp = test::call_service(&app, req).await;
    let body: serde_json::Value = test::read_body_json(resp).await;

    assert_eq!(body["buys"].as_array().unwrap().len(), 0);
    assert_eq!(body["sells"].as_array().unwrap().len(), 0);
}

#[actix_web::test]
async fn test_get_orderbook_partial_fill() {
    let app = test::init_service(create_test_app()).await;

    let server_id = Uuid::new_v4();

    // Place a sell order for 10
    let sell_req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "seller",
            "price": 100.0,
            "quantity": 10,
            "side": "sell"
        }))
        .to_request();
    test::call_service(&app, sell_req).await;

    // Place a buy order for 5 (partial fill)
    let buy_req = test::TestRequest::post()
        .uri("/api/orders")
        .set_json(&json!({
            "server_id": server_id,
            "item_id": "DIAMOND",
            "player_id": "buyer",
            "price": 105.0,
            "quantity": 5,
            "side": "buy"
        }))
        .to_request();
    test::call_service(&app, buy_req).await;

    // Get order book - should show remaining sell order
    let req = test::TestRequest::get()
        .uri("/api/orderbook/DIAMOND")
        .to_request();
    let resp = test::call_service(&app, req).await;
    let body: serde_json::Value = test::read_body_json(resp).await;

    assert_eq!(body["buys"].as_array().unwrap().len(), 0);
    assert_eq!(body["sells"].as_array().unwrap().len(), 1);
    assert_eq!(body["sells"][0]["quantity"], 5); // 10 - 5 = 5 remaining
}
