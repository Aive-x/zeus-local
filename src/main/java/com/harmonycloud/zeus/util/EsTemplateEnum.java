package com.harmonycloud.zeus.util;

/**
 * @author Administrator
 * @since 2020/1/17 14:59
 */
public enum EsTemplateEnum {
    //模板类型
    LOG_STASH("caas_logs", "{     \"order\": 2,     \"index_patterns\": [         \"{log-index-prefix}*\"     ],     \"settings\": {" +
            "         \"index\": {             \"analysis\": {                 \"normalizer\": {                    " +
            " \"keyword_lowercase\": {                         \"filter\": [                             \"lowercase\"           " +
            "              ],                         \"type\": \"custom\"                     }                 }             }  " +
            "       }     },     \"mappings\": {         \"doc\": {             \"properties\": {                 \"message\": { " +
            "                    \"type\": \"text\",                     \"analyzer\": \"ik_max_word\",                    " +
            " \"fields\": {                         \"keyword\": {                             \"type\": \"keyword\",           " +
            "                  \"ignore_above\": 256,                             \"normalizer\": \"keyword_lowercase\"          " +
            "               }                     }                 },                 \"offset\": {                    " +
            " \"type\": \"long\"                 },                 \"@timestamp\": {                     \"type\": \"date\"     " +
            "            },                 \"docker_container\": {                     \"type\": \"keyword\"                 }, " +
            "                \"k8s_pod\": {                     \"type\": \"keyword\"                 },                 " +
            "\"k8s_node_name\": {                     \"type\": \"keyword\"                 },                " +
            " \"k8s_pod_namespace\": {                     \"type\": \"keyword\"                 },                 \"source\": {" +
            "                     \"type\": \"keyword\"                 },                 \"index\": {                     " +
            "\"type\": \"keyword\"                 },                 \"k8s_container_name\": {                     " +
            "\"type\": \"keyword\"                 },                 \"k8s_resource_type\": {                    " +
            " \"type\": \"keyword\"                 },                 \"k8s_resource_name\": {                    " +
            " \"type\": \"keyword\"                 }             }         }     } } "),
    AUDIT("audit", "{     \"order\": 2,     \"index_patterns\": [         \"audit-*\"     ],     \"settings\": {   " +
            "      \"index\": {       \"max_result_window\": \"300000\"         }     },     \"mappings\": {      " +
            " \"user_op_audit\": {           \"properties\": {               \"actionChDesc\": {                     " +
            "\"type\": \"text\"                 }, \"clusterid\": { \"type\" : \"keyword\" },    \"actionEnDesc\": {  \"type\": \"text\"   " +
            "              },                 \"beginTime\": { \"type\": \"date\"}, \"actionTime\":{ \"type\": \"date\"}," +
            "                \"method\": {                     " +
            "\"type\": \"keyword\"                 },                 \"moduleChDesc\": {                    " +
            " \"type\": \"keyword\"                 },                 \"moduleEnDesc\": {                    " +
            " \"type\": \"text\"                 },                 \"project\": {                     \"type\": \"text\"        " +
            "         },                 \"remoteIp\": {                     \"type\": \"text\"                 },               " +
            "  \"requestParams\": {                     \"type\": \"text\"                 },                 \"response\": {    " +
            "                 \"type\": \"text\"                 },                 \"status\": {                   " +
            "  \"type\": \"text\"                 },                 \"subject\": {                     \"type\": \"keyword\"   " +
            "              },                 \"tenant\": {                     \"type\": \"text\"                 },           " +
            "      \"url\": {                     \"type\": \"text\"                 },                 \"user\": {             " +
            "        \"type\": \"text\"                 }           }       }   } }"),

    NGINX_LOG("nginxlog", "{     \"order\": 2,     \"index_patterns\": [         \"nginxlog-*\"     ],     " +
            "\"mappings\": {       \"doc\": {     \"properties\": { \"index\": {         \"type\": \"keyword\"       },      " +
            " \"request\": {         \"type\": \"keyword\"       },       \"upstream_addr\": {         \"type\": \"keyword\"    " +
            "   },       \"body_bytes_sent\": {         \"type\": \"keyword\"       },       \"source\": {        " +
            " \"type\": \"text\",                   \"fields\": {                       \"keyword\": {                          " +
            " \"type\": \"keyword\",                             \"ignore_above\": 256                         }                " +
            "     }       },       \"http_user_agent\": {         \"type\": \"keyword\"       },       \"remote_user\": {      " +
            "   \"type\": \"keyword\"       },       \"upstream_status\": {         \"type\": \"keyword\"       },      " +
            " \"request_time\": {         \"type\": \"float\"       },       \"upstream_cache_status\": {        " +
            " \"type\": \"keyword\"       },       \"remote_addr\": {         \"type\": \"keyword\"       },      " +
            " \"time_local\": {         \"type\": \"keyword\"       },       \"cookie_cmos_vision\": {         " +
            "\"type\": \"keyword\"       },       \"@timestamp\": {         \"type\": \"date\"       },       \"http_referer\": { " +
            "        \"type\": \"keyword\"       },       \"http_x_forwarded_for\": {         \"type\": \"keyword\"       },    " +
            "   \"upstream_response_time\": {         \"type\": \"keyword\"       },       \"status\": {         " +
            "\"type\": \"keyword\"       }     }     } } }"),

    STDOUT("stdout", "{     \"order\": 2,     \"index_patterns\": [         \"stdout-*\"     ],     \"settings\": {" +
            "         \"index\": {             \"analysis\": {                 \"normalizer\": {                    " +
            " \"keyword_lowercase\": {                         \"filter\": [                             \"lowercase\"           " +
            "              ],                         \"type\": \"custom\"                     }                 }             }  " +
            "       }     },     \"mappings\": {         \"doc\": {             \"properties\": {                 \"message\": { " +
            "                    \"type\": \"text\",                     \"analyzer\": \"ik_max_word\",                    " +
            " \"fields\": {                         \"keyword\": {                             \"type\": \"keyword\",           " +
            "                  \"ignore_above\": 256,                             \"normalizer\": \"keyword_lowercase\"          " +
            "               }                     }                 },                 \"offset\": {                    " +
            " \"type\": \"long\"                 },                 \"@timestamp\": {                     \"type\": \"date\"     " +
            "            },                 \"docker_container\": {                     \"type\": \"keyword\"                 }, " +
            "                \"k8s_pod\": {                     \"type\": \"keyword\"                 },                 " +
            "\"k8s_node_name\": {                     \"type\": \"keyword\"                 },                " +
            " \"k8s_pod_namespace\": {                     \"type\": \"keyword\"                 },                 \"source\": {" +
            "                     \"type\": \"keyword\"                 },                 \"index\": {                     " +
            "\"type\": \"keyword\"                 },                 \"k8s_container_name\": {                     " +
            "\"type\": \"keyword\"                 },                 \"k8s_resource_type\": {                    " +
            " \"type\": \"keyword\"                 },                 \"k8s_resource_name\": {                    " +
            " \"type\": \"keyword\"                 }             }         }     } } "),

    MYSQL_SLOW_LOG("mysqlslowlog", "{ \"order\": 2, \"index_patterns\": [\"mysqlslowlog-*\"]," +
            " \"settings\":{\"index\":{\"max_result_window\":\"300000\"}}, \"mappings\":{}}");

    private String name;
    private String code;


    EsTemplateEnum(String name, String code) {
        this.setCode(code);
        this.setName(name);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
