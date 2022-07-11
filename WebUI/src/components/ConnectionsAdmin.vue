<template>
  <div id="connections">
    <b-breadcrumb :items="breadcrumbs"></b-breadcrumb>
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
      <h1 class="h2">Connections</h1>
      <div class="btn-toolbar mb-2 mb-md-0">
        <div class="btn-group mr-2">
          <b-button
            v-if="isPathPartnerList"
            @click="newObject()"
            variant="success"
            size="sm"
            >New</b-button
          >
          <!-- <button type="button" class="btn btn-sm btn-outline-secondary">Export</button> -->
        </div>
      </div>
    </div>
    <table-custom
      :loading="loadingTable"
      :items="items"
      :fields="fields"
      @deleteObject="deleteObject"
      @editObject="editObject"
    ></table-custom>
    <b-modal
      hide-footer
      :id="infoModal.id"
      :title="infoModal.title"
      ok-only
      @hidden="resetModal"
      no-close-on-esc
      no-close-on-backdrop
    >
      <form-custom
        :loading="loadingForm"
        v-if="infoModal.title"
        title="Partner Editor"
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
import TableCustom from "./TableCustom";
import Utils from "../utils";
import Swal from "sweetalert2";
var _ = require("lodash");
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
          text: "Connections",
          to: { name: "Connections" },
          active: true,
        },
      ],
      schema: {
        fields: [
          {
            type: "text",
            name: "name",
            label: "Partnership ID",
            placeholder: "Connection's Unique Key Name",
            value: "",
            _validate: {
              required: true,
              regex: `[^&<>"']`,
            },
          },
          {
            type: "select",
            name: "receiverIDs",
            label: "Receiving Partner",
            placeholder: "Connection's Receiver",
            list: [],
            value: "",
            _validate: {
              required: true,
            },
          },
          {
            type: "select",
            name: "senderIDs",
            label: "Sending Partner",
            placeholder: "Connection's Sender",
            list: [],
            value: "",
            _validate: {
              required: true,
            },
          },
          {
            type: "url",
            name: "as2_url",
            label: "AS2 URL",
            placeholder: "i.e.  http://www.MyPartnerAS2Machine.com:10080",
            value: "",
            _validate: {
              required: true,
              url: true,
              regex: `[^&<>"']`,
            },
          },
          {
            type: "text",
            name: "subject",
            label: "AS2 SMIME Subject",
            placeholder: "i.e.  AS2 Transfer",
            value: "",
            _validate: {
              required: true,
              regex: `[^&<>"']`,
            },
          },
          {
            type: "email",
            name: "as2_mdn_to",
            label: "MDN response To",
            placeholder: "i.e.  datamanager@mypartner.com",
            value: "",
            _validate: {
              required: true,
              email: true,
            },
          },
          {
            type: "select",
            name: "encrypt",
            label: "Encryption Mode",
            list: [
              "",
              "3DES",
              "CAST5",
              "RC2_CBC",
              "AES128",
              "AES192",
              "AES256",
            ],
            value: "3DES",
            _validate: {
              required: true,
            },
          },
          {
            type: "select",
            name: "sign",
            label: "Signature Mode",
            list: ["", "SHA1", "SHA256", "SHA384", "SHA512", "RC4", "MD5"],
            value: "SHA1",
            _validate: {
              required: true,
            },
          },
          {
            type: "url",
            name: "as2_receipt_option",
            label: "Asynchronous MDN server's URL",
            placeholder: "i.e.  http://www.MyAS2Machine.com:10081",
            value: "",
            _validate: {
              required: true,
              url: true,
              regex: `[^&<>"']`,
            },
          },
          {
            type: "text",
            name: "as2_mdn_options",
            label: "MDN option values for E-mail header",
            placeholder:
              "i.e.  signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha1",
            value:
              "signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha1",
            _validate: {
              required: true,
            },
          },
          {
            type: "checkbox",
            name: "prevent_canonicalization_for_mic",
            label: "Prevent Canonicalization for MIC",
            value: false,
            _validate: {
              required: true,
            },
          },
          {
            type: "checkbox",
            name: "remove_cms_algorithm_protection_attrib",
            label: "Remove CMS Protection",
            value: false,
            _validate: {
              required: true,
            },
          },
          {
            type: "checkbox",
            name: "rename_digest_to_old_name",
            label: "Support Digest with Oldname",
            value: false,
            _validate: {
              required: true,
            },
          },
          {
            type: "select",
            name: "content_transfer_encoding",
            label: "Transfer Encoding",
            list: ["8bit", "binary"],
            value: "8bit",
            _validate: {
              required: true,
            },
          },
        ],
      },
      fields: [
        {
          key: "key",
          label: "#",
          sortable: true,
          class: "text-center",
        },
        {
          key: "name",
          label: "Connection ID",
          sortable: true,
          class: "text-center",
        },
        { key: "actions", label: "Actions", class: "text-center" },
      ],
      infoModal: {
        id: "info-modal",
        title: "",
      },
      items: [],
      item: {
        _id: null,
        name: "",
        receiverIDs: "",
        senderIDs: "",
        as2_url: "",
        subject: "",
        as2_mdn_to: "",
        encrypt: "3DES",
        sign: "SHA1",
        as2_receipt_option: "",
        as2_mdn_options:
          "signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha1",
        prevent_canonicalization_for_mic: false,
        remove_cms_algorithm_protection_attrib: false,
        rename_digest_to_old_name: false,
        content_transfer_encoding: "8bit",
        compression_type: "",
        resend_max_retries: "",
        protocol: "",
      },
      loadingTable: false,
      loadingForm: false,
    };
  },
  components: {
    FormCustom,
    TableCustom,
  },
  computed: {
    isPathPartnerList() {
      return this.$route.name == "Connections";
    },
  },
  mounted: async function () {
    var list = await Utils.Crud.getList("partner");
    this.schema.fields[1].list = _.map(list, (item) => {
      return { value: item, text: item };
    });
    this.schema.fields[2].list = _.map(list, (item) => {
      return { value: item, text: item };
    });
    await this.getList();
  },
  methods: {
    clearItem: function () {
      this.item = {
        _id: null,
        name: "",
        receiverIDs: "",
        senderIDs: "",
        as2_url: "",
        subject: "",
        as2_mdn_to: "",
        encrypt: "3DES",
        sign: "SHA1",
        as2_receipt_option: "",
        as2_mdn_options:
          "signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha1",
        prevent_canonicalization_for_mic: false,
        remove_cms_algorithm_protection_attrib: false,
        rename_digest_to_old_name: false,
        content_transfer_encoding: "8bit",
        compression_type: "",
        resend_max_retries: "",
        protocol: "",
      };
    },
    resetModal: function () {
      this.infoModal.title = "";
      this.clearItem();
      this.$root.$emit("bv::hide::modal", this.infoModal.id);
      this.loadingForm = this.loadingTable = false;
    },
    deleteObject: async function (item) {
      this.loadingTable = true;
      try {
        Swal.fire({
          title: "Are you sure to delete?",
          html: `a partnership  ${item.name}`,
          showCancelButton: true,
          confirmButtonText: "Yes",
          cancelButtonText: "No",
        }).then(async (result) => {
          if (result.isConfirmed) {
            Utils.Crud.deleteObject("partnership", item.key)
              .then((response) => {
                Swal.fire("Deleted!", "", "success");
                this.getList();
              })
              .catch((e) => {
                console.log("delete parner", e);
                Swal.fire("Error!", e, "error");
              });
            this.loadingTable = false;
          } else {
            this.loadingTable = false;
          }
        });
      } catch (e) {
        console.log(e);
        Swal.fire("Error!", e, "error");
        this.loadingTable = false;
      }
    },
    newObject: function () {
      this.loadingTable = true;
      this.infoModal.title = "New connection";
      this.clearItem();
      this.$root.$emit("bv::show::modal", this.infoModal.id);
    },
    editObject: async function (_item) {
      this.loadingTable = true;

      this.infoModal.title = "Edit connection";
      // var newObj = this.getNewObject();
      const result = await this.getObject(_item.key);
      console.log("result santos", result);
      // await Utils.Crud.getObject("partnership", _item.key, newObj);
      // console.log("resutl", result);
      this.item = Object.assign(this.item, result);
      this.$root.$emit("bv::show::modal", this.infoModal.id);
    },
    getNewObject: function () {
      var obj = { _id: null };
      this.schema.fields.forEach((f) => (obj[f.name] = f.value));
      return obj;
    },
    getObject: async function (index) {
      var newObj = this.getNewObject();
      var item = await Utils.Crud.getObject("partnership", index, newObj);
      newObj._id = item._id;
      newObj.name = item.name;
      // Post process object to match form compatibility
      if (item.senderIDs != null) {
        newObj.senderIDs = item.senderIDs.name;
      }
      if (item.receiverIDs != null) {
        newObj.receiverIDs = item.receiverIDs.name;
      }
      for (var i in item.attributes) {
        newObj[i] = item.attributes[i];
      }
      return newObj;
    },
    getList: async function () {
      this.items = await Utils.Crud.getList("partnership").then((results) => {
        return _.map(results, (item, index) => {
          return {
            key: index,
            name: item,
            actions: {
              edit: { name: "Edit", show: true },
              delete: { name: "Delete", show: true },
            },
          };
        });
      });
    },
    saveObject: async function (data) {
      this.loadingForm = true;
      try {
        data._prefix = `0=${data.senderIDs}&1=${data.receiverIDs}`;
        console.log("save data222", data);
        await Utils.Crud.saveObject("partnership", data).then(()=>{
          this.loadingTable = this.loadingForm = true;
        });
        this.resetModal();
        await this.getList();
      } catch (e) {
        Swal.fire("Error!", e, "error");
         this.loadingForm = true;
      }
    },
  },
};
</script>