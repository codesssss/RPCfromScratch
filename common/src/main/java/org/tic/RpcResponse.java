package org.tic;

import lombok.*;

/**
 * @author Xuhang Shi
 * @date 27/11/2023 9:51 pm
 */

@AllArgsConstructor
@Getter
@NoArgsConstructor
@Builder
@ToString
public class RpcResponse {
    private String message;
}
