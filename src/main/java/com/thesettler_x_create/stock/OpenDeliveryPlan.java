package com.thesettler_x_create.stock;

/**
 * What a request with deliveries still open may do this tick: reserve free rack stock, order from
 * the network, hand out new deliveries.
 *
 * <p>While deliveries are open some items exist twice in the numbers: a delivery nobody picked up
 * yet is both "in delivery" and still reserved. Each decision leans to the side that cannot hurt.
 *
 * <ul>
 *   <li>Reserving and ordering count every open delivery as covered, on top of the reservations.
 *       That can underestimate what is missing, so the rest is ordered a little later, but it never
 *       orders twice.
 *   <li>New deliveries subtract the deliveries not confirmed as picked up from the reservations.
 *       After a reload nothing is confirmed, so fewer deliveries go out now and the rest after the
 *       open ones, but never a second delivery for the same items.
 * </ul>
 *
 * @param reserveFromRack free rack stock to reserve for the request
 * @param missingBeforeInflight amount to hand to the network order, which still subtracts what the
 *     request has on its way
 * @param deliverNow reserved stock to hand out in new deliveries
 */
public record OpenDeliveryPlan(int reserveFromRack, int missingBeforeInflight, int deliverNow) {

  /**
   * @param needed requested amount minus leftover and minus what was already delivered
   * @param reserved rack stock reserved for the request
   * @param inDelivery items in open deliveries of the request, picked up or not
   * @param inDeliveryNotPickedUp items in open deliveries not confirmed as picked up
   * @param ownInflight items on their way from the network for the request
   * @param freeRack rack stock of accepted items no request has reserved
   */
  public static OpenDeliveryPlan of(
      int needed,
      int reserved,
      int inDelivery,
      int inDeliveryNotPickedUp,
      int ownInflight,
      int freeRack) {
    int reservedNow = Math.max(0, reserved);
    int delivering = Math.max(0, inDelivery);
    int uncovered = Math.max(0, needed - reservedNow - delivering - Math.max(0, ownInflight));
    int reserveFromRack = Math.min(Math.max(0, freeRack), uncovered);
    int reservedAfter = reservedNow + reserveFromRack;
    int missingBeforeInflight = Math.max(0, needed - reservedAfter - delivering);
    int notPickedUp = Math.min(delivering, Math.max(0, inDeliveryNotPickedUp));
    int deliverNow = Math.max(0, reservedAfter - notPickedUp);
    return new OpenDeliveryPlan(reserveFromRack, missingBeforeInflight, deliverNow);
  }
}
