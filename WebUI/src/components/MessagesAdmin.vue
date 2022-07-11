<template>
  <div id="messages">
    <b-breadcrumb v-if="isPathMessageList" :items="breadcrumbs"></b-breadcrumb>
    <div
      class="
        d-flex
        justify-content-between
        flex-wrap flex-md-nowrap
        align-items-center
        pt-3
        pb-2
        mb-3
        border-bottom
      "
    >
      <h1 class="h2">Messages</h1>
      <div class="btn-toolbar mb-2 mb-md-0">
        <div class="btn-group mr-2">
          <!-- @click="newObject()" -->
          <b-button
            v-if="isPathMessageList"
            @click="getList()"
            variant="primary"
            size="sm"
          >
            <b-icon icon="arrow-repeat"></b-icon>
            Refresh</b-button
          >
          <b-button @click="exportPdf" variant="danger" size="sm">
            <b-icon icon="file-earmark-pdf-fill"></b-icon>
            Export</b-button
          >
          <b-button @click="exportExcel" variant="success" size="sm">
            <b-icon icon="file-earmark-excel-fill"></b-icon>
            Export</b-button
          >
          <!-- <button type="button" class="btn btn-sm btn-outline-secondary">Export</button> -->
        </div>
      </div>
    </div>
    <filter-message-table
      :filter="filter"
      :filterField="filterField"
      @filterItems="filterItems"
    ></filter-message-table>
    <div id="table-message">
      <table-custom-message
        ref="table-messages"
        :items="getFilterItems"
        :fields="fields"
        :filter="filter"
        :stateMap="stateMap"
        :showFilter="showFilter"
        @deleteObject="deleteObject"
        @editObject="editObject"
        @showObject="showObject"
        ><div class="frame">
          <slot name="filter">Title</slot>
        </div>
      </table-custom-message>
    </div>
    <b-modal hide-footer :id="infoModal.id" :title="infoModal.title" ok-only>
      <form-custom
        v-if="infoModal.title"
        title="Message Editor"
        :schema="schema"
        :model="item"
        @commit="saveObject"
        @revert="resetModal"
      >
      </form-custom>
    </b-modal>
  </div>
</template>
<script>
import FormCustom from "./FormCustom";
import TableCustomMessage from "./TableCustomMessage";
import FilterMessageTable from "./FilterMessageTable";

import Utils from "../utils";
import Swal from "sweetalert2";
var _ = require("lodash");
import { jsPDF } from "jspdf";
import autoTable from "jspdf-autotable";
import moment from "moment";
// var momentTz= require("moment-timezone")
var XLSX = require("xlsx");

export default {
  data: function () {
    return {
      breadcrumbs: [
        {
          text: "Dashboard",
          to: { name: "Dashboard" },
          active: false,
        },
        {
          text: "Messages",
          to: { name: "Messages" },
          active: true,
        },
      ],
      filter: {
        text: "",
        sender_id: "",
        receiver_id: "",
        states: ["ok", "pending", "stop"],
        dateTime: "America/Caracas",
      },
      stateMap: {
        ok: [
          "msg_send_start",
          "msg_receive_start",
          "mdn_send_start",
          "mdn_receive_start",
          "msg_sent_mdn_received_ok",
          "msg_rxd_mdn_sent_ok",
          "msg_rxd_mdn_not_requested_ok",
          "msg_sent_mdn_received_mic_mismatch",
        ],
        pending: [
          "msg_send_exception",
          "msg_receive_exception",
          "mdn_sending_exception",
          "mdn_receiving_exception",
        ],
        stop: [
          "msg_send_fail",
          "msg_send_fail_resend_queued",
          "msg_receive_fail",
          "msg_rxd_asyn_mdn_send_fail_resend_queued",
          "mdn_asyn_receive_fail",
          "msg_rxd_mdn_sending_fail",
          "msg_receive_error_sending_mdn_error",
          "msg_sent_mdn_received_error",
        ],
      },

      showFilter: false,
      filterField: {
        locations: [
          {
            type: "select",
            name: "sender_id",
            label: "Local station",
            list: [
              {
                value: "",
                text: "All",
              },
            ],
          },
          {
            type: "select",
            name: "receiver_id",
            label: "Remote station",
            list: [
              {
                value: "",
                text: "All",
              },
            ],
          },
        ],
        states: [
          {
            text: "Display ok",
            value: "ok",
            badge: "success",
            ab: "O",
          },
          {
            text: "Display pending",
            value: "pending",
            badge: "warning",
            ab: "P",
          },
          { text: "Display stop", value: "stop", badge: "danger", ab: "S" },
        ],
        dateTimes: [
          {
            type: "radio",
            text: "Local[America/Caracas; -04:00]",
            value: "America/Caracas",
          },
          {
            type: "radio",
            text: "AS2 Server[Europe/Berlin; +02:00]",
            value: "Europe/Berlin",
          },
        ],
      },
      schema: {
        fields: [
          {
            key: "name",
            type: "text",
            name: "name",
            label: "Message ID",
            placeholder: "Trading Message's Unique Key Name",
            _validate: {
              required: true,
            },
          },
          {
            type: "text",
            name: "as2_id",
            label: "AS2 ID",
            placeholder: "Trading Message's AS2 ID",
            _validate: {
              required: true,
            },
          },
          {
            type: "email",
            name: "email",
            label: "Email",
            placeholder: "Trading Message's Email Address",
            _validate: {
              required: true,
              email: true,
            },
          },
          {
            type: "select",
            name: "x509_alias",
            label: "Certificate Alias",
            // placeholder: "X.509 Certificate Alias on the KeyStore",
            _validate: {
              required: true,
            },
            list: [],
          },
        ],
      },
      
      infoModal: {
        id: "info-modal",
        title: "",
      },
      items: [],

      item: {
        _id: null,
        name: "",
        as2_id: "",
        email: "",
        x509_alias: "",
      },
    };
  },
  components: {
    FormCustom,
    TableCustomMessage,
    FilterMessageTable,
  },
  computed: {
    isPathMessageList() {
      return this.$route.name == "Messages";
    },
    getFilterItems() {
      let _list = this.items;
      if (this.filter.sender_id != "") {
        _list = _.filter(_list, (item) => {
          return item.sender_id == this.filter.sender_id;
        });
      }
      if (this.filter.receiver_id != "") {
        _list = _.filter(_list, (item) => {
          return item.receiver_id == this.filter.receiver_id;
        });
      }
      if (this.filter.states.length == 0) {
        _list = [];
      } else {
        let _list2 = [];
        this.filter.states.forEach((display) => {
          let aux = _.filter(_list, (item) => {
            return (
              _.find(this.stateMap[display], (m) => {
                return item.state == m;
              }) && item
            );
          });
          _list2 = _.concat(_list2, aux);
        });
        _list = _list2;
      }
      // if (this.filter.dateTime != "") {
      //   _list.forEach((item) => {
      //     item.create_dt =
      //       this.filter.dateTime == "America/Caracas"
      //         ? 
      //         // moment( item.create_dt)
      //         //     .utc(item.create_dt)
      //         //     .toDate()
      //             moment(item.create_dt).zone("-08:00")
      //             .format("YYYY-DD-MM H:mm:ss")
      //         :
      //         moment(item.create_dt).zone("-04:00")
      //             .format("YYYY-DD-MM H:mm:ss")
      //         // moment()
      //         //     .utc(this.filter.dateTime, item.create_dt)
      //         //     .format("YYYY-DD-MM H:mm:ss");
      //   });
      // }
      return _list;
    },
    fields() {
      return [
        {
          key: "key",
          label: "#",
          sortable: true,
          class: "text-center",
          filter: false,
          export: true,
        },
        {
          key: "state",
          label: "Display",
          sortable: false,
          class: "text-center",
          filter: true,
          export: true,
        },
        {
          key: "create_dt",
          label: "Timestamp",
          sortable: true,
          class: "text-center",
          formatter: (value, key, item) => {
              return this.filter.dateTime == "America/Caracas"
              ? 
              moment.tz(value, this.filter.dateTime).utc()
                  // moment(value).zone("-04:00")
                  .format("YYYY-DD-MM H:mm:ss")
              :
               moment.tz(value, this.filter.dateTime).utc()
                  // moment(value).zone("-04:00")
                  .format("YYYY-DD-MM H:mm:ss")
              // moment(value).zone("+02:00")
              //     .format("YYYY-DD-MM H:mm:ss")
          },
          filter: true,
          export: true,
        },
        {
          key: "sender_id",
          label: "Local Location",
          sortable: true,
          class: "text-center",
          filter: true,
          export: true,
        },
        {
          key: "receiver_id",
          label: "Remote Location",
          sortable: true,
          class: "text-center",
          filter: true,
          export: true,
        },
        {
          key: "msg_id",
          label: "Message ID",
          sortable: true,
          class: "text-center",
          filter: false,
          export: true,
        },
        {
          key: "file_name",
          label: "Payload",
          sortable: true,
          class: "text-center",
          filter: false,
          export: true,
        },
        {
          key: "encryption_algorithm",
          label: "Encryption",
          sortable: true,
          class: "text-center",
          filter: false,
          export: true,
        },
        {
          key: "signature_algorithm",
          label: "Signature",
          sortable: true,
          class: "text-center",
          filter: false,
          export: true,
        },
        {
          key: "mdn_mode",
          label: "MDN",
          sortable: true,
          class: "text-center",
          filter: false,
          export: true,
        },
     
      ]}
  },
  mounted: async function () {
    var list = await Utils.Crud.getList("cert");
    this.schema.fields[3].list = _.map(list, (item) => {
      return { value: item, text: item };
    });
    await this.getList();
    await this.getListPartners();
  },
  methods: {
    exportExcel() {
      const fileName = "table-" + moment().format("YYYYMMDDH:mm:ss") + ".xlsx";
      let _items = _.map(this.getFilterItems, (_item) => {
        let obj = {};
        _.forIn(_item, function (value, key) {
          if (typeof _item[key] === "string") {
            obj[key] = value;
          }
        });
        return obj;
      });
      const ws = XLSX.utils.json_to_sheet(_items);
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, "output");
      XLSX.writeFile(wb, fileName);
    },
    exportPdf() {
      const doc = new jsPDF({
        orientation: "landscape",
      });
      let head = _.map(
        _.filter(this.fields, (field) => field.export),
        (_field) => {
          return { header: _field.label, dataKey: _field.key };
        }
      );
      console.log("head", head);
      let body = _.map(this.getFilterItems, (item) => {
        // return [
        //   item.key,
        //   item.state,
        //   item.create_dt,
        //   item.sender_id,
        //   item.receiver_id,
        //   item.msg_id,
        //   item.file_name,
        //   item.encryption_algorithm,
        //   item.signature_algorithm,
        //   item.mdn_mode,
        // ];
        return {
        "key": item.key,
        "state": item.state,
        "create_dt": item.create_dt,
        "sender_id": item.sender_id,
        "receiver_id": item.receiver_id,
        "msg_id": item.msg_id,
        "file_name": item.file_name,
        "encryption_algorithm": item.encryption_algorithm,
        "signature_algorithm": item.signature_algorithm,
        "mdn_mode": item.mdn_mode,
        }
        // console.log("value,key",value);
        // return JSON.parse(JSON.stringify(item))
      });
      // _.map(this.getFilterItems, (_item, index) => {
      //   let array = [];
      //   array.push(index);
      //   _.forIn(_item, function (value, key) {
      //     if (typeof _item[key] === "string") {
      //       array.push(value);
      //     }
      //   });
      //   return array;
      // });
      console.log("body", body);

      // var doc = new jsPDF();
      // doc.autoTable(head, body, {
      //     columnStyles: {

      //     },
      // });

      autoTable(doc, {
        columnStyles: { 5: { halign: "center", cellWidth: 50 },6: { halign: "center", cellWidth: 40 } },
        columns:head,
        body: body,
      });
      doc.save("table-" + moment().format("YYYYMMDDH:mm:ss") + ".pdf");
      // doc.autoPrint(this.fields, this.items2);
      // doc.save("two-by-four.pdf");
    },
    filterItems: function (data) {
    },
    resetModal: function () {
      this.infoModal.title = "";
      this.item = {
        _id: null,
        name: "",
        as2_id: "",
        email: "",
        x509_alias: "",
      };
      this.$root.$emit("bv::hide::modal", this.infoModal.id);
    },
    deleteObject: async function (item) {
      try {
        Swal.fire({
          title: "Are you sure to delete?",
          html: `a partner  ${item.name}`,
          icon: "warning",
          showCancelButton: true,
          confirmButtonText: "Yes",
          cancelButtonText: "No",
        }).then(async (result) => {
          if (result.isConfirmed) {
            Utils.Crud.deleteObject("partner", item.key)
              .then((response) => {
                Swal.fire("Deleted!", "", "success");
                this.getList();
              })
              .catch((e) => {
                console.log("delete parner", e);
                Swal.fire("Error!", e, "error");
              });
          }
        });
      } catch (e) {
        console.log(e);
        Swal.fire("Error!", e, "error");
      }
    },
    newObject: function () {
      this.infoModal.title = "New partner";
      this.item = {
        _id: null,
        name: "",
        as2_id: "",
        email: "",
        x509_alias: "",
      };
      this.$root.$emit("bv::show::modal", this.infoModal.id);
    },
    editObject: async function (_item) {
      this.infoModal.title = "Message Edit";
      var newObj = this.getNewObject();
      const result = await Utils.Crud.getObject("partner", _item.key, newObj);
      this.item = Object.assign(this.item, result);
      this.$root.$emit("bv::show::modal", this.infoModal.id);
    },
    showObject: async function (_item) {
      this.$router.push({ name: "MessageDetail", params: { id: _item.key } });
    },
    getNewObject: function () {
      var obj = { _id: null };
      this.schema.fields.forEach((f) => (obj[f.name] = f.value));
      return obj;
    },
    getList: async function () {
      this.items = await Utils.Crud.getList("messages").then((results) => {
        console.log("results", results);
        return _.map(results, (item, index) => {
          return {
            key: index,
            id:item.ID,
            state: item.STATE,
            create_dt: item.CREATE_DT,
            sender_id: item.SENDER_ID,
            receiver_id: item.RECEIVER_ID,
            msg_id: item.MSG_ID,
            file_name: item.FILE_NAME,
            encryption_algorithm: item.ENCRYPTION_ALGORITHM
              ? item.ENCRYPTION_ALGORITHM
              : "Unknown",
            signature_algorithm: item.SIGNATURE_ALGORITHM
              ? item.SIGNATURE_ALGORITHM
              : "Unknown",
            mdn_mode: item.MDN_MODE,
            // actions: {
            //   show: { name: "Detail", show: true },
            //   delete: { name: "Delete", show: false },
            //   edit: { name: "Edit", show: false },
            // },
          };
        });
      });
    },
    getListPartners: async function () {
      let list = _.map(await Utils.Crud.getList("partner"), (item) => {
        return { value: item, text: item };
      });
      let _list = _.concat(this.filterField.locations[0].list, list);

      this.filterField.locations[0].list = _list;
      this.filterField.locations[1].list = _list;
    },
    saveObject: async function (data) {
      try {
        await Utils.Crud.saveObject("partner", data);
        this.resetModal();
        await this.getList();
      } catch (e) {
        Swal.fire("Error!", e, "error");
      }
    },
  },
};
</script>
<style >
</style>